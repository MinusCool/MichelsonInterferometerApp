import sys
import json
import traceback
import base64
from io import BytesIO
import numpy as np
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

try:
    from scipy.signal import (
        savgol_filter,
        butter,
        filtfilt,
        medfilt,
        windows,
        find_peaks,
    )
    HAVE_SCIPY = True
except Exception:
    HAVE_SCIPY = False


# =========================================================
# KALMAN STATE
# =========================================================
_kalman_state = {}   # ch -> (x, P)
_last_version = {}   # ch -> paramsVersion
_filtered_history = {}  # ch -> {"version": int, "values": list[float]}
_raw_history = {}  # ch -> {"version": int, "values": list[float]}


# =========================================================
# FILTER CORE
# =========================================================
def kalman_process(ch: int, chunk: np.ndarray, q: float, r: float, version: int) -> np.ndarray:
    lv = _last_version.get(ch)
    if lv is None or lv != version:
        _kalman_state.pop(ch, None)
        _last_version[ch] = version

    st = _kalman_state.get(ch)
    if st is None:
        x = float(chunk[0]) if chunk.size else 0.0
        P = 1.0
    else:
        x, P = st

    out = np.empty(chunk.shape[0], dtype=np.float64)
    for i, z in enumerate(chunk.astype(np.float64, copy=False)):
        x_pred = x
        P_pred = P + q
        K = P_pred / (P_pred + r)
        x = x_pred + K * (z - x_pred)
        P = (1.0 - K) * P_pred
        out[i] = x

    _kalman_state[ch] = (x, P)
    return out


def _sg_fallback_numpy(x: np.ndarray, window: int, poly: int) -> np.ndarray:
    x = x.astype(np.float64, copy=False)
    n = x.size
    if n < 3:
        return x.copy()

    if window % 2 == 0:
        window += 1
    window = max(3, min(window, n if n % 2 == 1 else max(3, n - 1)))
    poly = max(1, min(poly, window - 1))
    half = window // 2

    out = np.empty(n, dtype=np.float64)
    for i in range(n):
        left = max(0, i - half)
        right = min(n, i + half + 1)
        xs = np.arange(left, right, dtype=np.float64) - i
        ys = x[left:right]
        fit_deg = min(poly, len(xs) - 1)
        if fit_deg <= 0:
            out[i] = float(np.mean(ys))
        else:
            coeffs = np.polyfit(xs, ys, fit_deg)
            out[i] = float(np.polyval(coeffs, 0.0))
    return out


def sg_process_full(x: np.ndarray, window: int, poly: int) -> np.ndarray:
    if window % 2 == 0:
        window += 1
    window = max(3, min(window, 301))
    poly = max(2, min(poly, min(10, window - 1)))

    if x.size < window:
        window = x.size if x.size % 2 == 1 else max(3, x.size - 1)
        poly = min(poly, max(2, window - 1))
        if window < 3:
            return x.astype(np.float64, copy=False)

    x64 = x.astype(np.float64, copy=False)
    if HAVE_SCIPY:
        return savgol_filter(
            x64,
            window_length=window,
            polyorder=poly,
            mode="interp",
        )
    return _sg_fallback_numpy(x64, window, poly)


# =========================================================
# PEAK COUNT
# =========================================================
def _local_prominence(values: np.ndarray, idx: int, half_window: int) -> float:
    left = max(0, idx - half_window)
    right = min(len(values), idx + half_window + 1)
    if idx <= left or idx >= right - 1:
        return 0.0
    left_min = float(np.min(values[left:idx]))
    right_min = float(np.min(values[idx + 1:right]))
    return float(values[idx] - max(left_min, right_min))


def _detect_peaks_fallback(
    values: np.ndarray,
    min_peak_distance: int,
    peak_threshold: float,
    prominence_threshold: float,
    prominence_window: int,
) -> np.ndarray:
    peaks = []
    last_peak = -10_000

    for i in range(1, len(values) - 1):
        prev_v = values[i - 1]
        curr_v = values[i]
        next_v = values[i + 1]

        is_local_max = ((curr_v > prev_v and curr_v >= next_v) or
                        (curr_v >= prev_v and curr_v > next_v))
        if not is_local_max:
            continue

        height_ok = curr_v >= peak_threshold
        dist_ok = (i - last_peak) >= min_peak_distance
        prominence_ok = _local_prominence(values, i, prominence_window) >= prominence_threshold

        if height_ok and dist_ok and prominence_ok:
            peaks.append(i)
            last_peak = i

    return np.array(peaks, dtype=np.int64)


def detect_peaks_on_filtered(
    values: np.ndarray,
    min_peak_distance: int = 3,
    rel_height: float = 0.15,
    rel_prominence: float = 0.08,
    prominence_window: int = 12,
) -> np.ndarray:
    if values.size < 3:
        return np.array([], dtype=np.int64)

    min_v = float(np.min(values))
    max_v = float(np.max(values))
    span = max_v - min_v
    if span < 1e-12:
        return np.array([], dtype=np.int64)

    peak_threshold = min_v + (rel_height * span)
    prominence_threshold = max(span * rel_prominence, 1e-9)
    distance = max(1, int(min_peak_distance))
    prom_window = max(distance + 1, int(prominence_window))

    if HAVE_SCIPY:
        peaks, _ = find_peaks(
            values,
            height=peak_threshold,
            distance=distance,
            prominence=prominence_threshold,
        )
        return peaks.astype(np.int64, copy=False)

    return _detect_peaks_fallback(
        values,
        min_peak_distance=distance,
        peak_threshold=peak_threshold,
        prominence_threshold=prominence_threshold,
        prominence_window=prom_window,
    )


def count_fringes_from_peaks(
    values: np.ndarray,
    min_peak_distance: int = 3,
    rel_height: float = 0.15,
    rel_prominence: float = 0.08,
    prominence_window: int = 12,
) -> int:
    peaks = detect_peaks_on_filtered(
        values,
        min_peak_distance=min_peak_distance,
        rel_height=rel_height,
        rel_prominence=rel_prominence,
        prominence_window=prominence_window,
    )
    return int(len(peaks))


def update_global_peak_count(
    ch: int,
    version: int,
    y_chunk: np.ndarray,
    min_peak_distance: int = 3,
    rel_height: float = 0.15,
    rel_prominence: float = 0.08,
    prominence_window: int = 12,
    startup_trim_enabled: bool = False,
    startup_trim_samples: int = 0,
) -> int:
    state = _filtered_history.get(ch)
    if state is None or int(state.get("version", -1)) != version:
        state = {"version": version, "values": []}
        _filtered_history[ch] = state

    if y_chunk.size:
        state["values"].extend(np.asarray(y_chunk, dtype=np.float64).tolist())

    values = np.asarray(state["values"], dtype=np.float64)
    if startup_trim_enabled:
        trim_idx = int(max(0, startup_trim_samples))
        values = values[trim_idx:] if trim_idx < values.size else np.array([], dtype=np.float64)

    return count_fringes_from_peaks(
        values,
        min_peak_distance=min_peak_distance,
        rel_height=rel_height,
        rel_prominence=rel_prominence,
        prominence_window=prominence_window,
    )


def update_raw_history(
    ch: int,
    version: int,
    raw_chunk: np.ndarray,
) -> np.ndarray:
    state = _raw_history.get(ch)
    if state is None or int(state.get("version", -1)) != version:
        state = {"version": version, "values": []}
        _raw_history[ch] = state

    if raw_chunk.size:
        state["values"].extend(np.asarray(raw_chunk, dtype=np.float64).tolist())

    return np.asarray(state["values"], dtype=np.float64)

# =========================================================
# FFT HELPERS
# =========================================================
def robust_remove_baseline(y: np.ndarray, kernel_ratio: float = 0.03) -> np.ndarray:
    if y.size == 0:
        return y

    if not HAVE_SCIPY:
        return y - np.median(y)

    n = len(y)
    k = max(5, int(n * kernel_ratio))
    if k % 2 == 0:
        k += 1
    if k >= n:
        k = n - 1 if n % 2 == 0 else n
        if k < 5:
            return y - np.median(y)

    baseline = medfilt(y, kernel_size=k)
    return y - baseline


def butter_filter(x: np.ndarray, fs: float, cutoff, order: int = 4, btype: str = "high") -> np.ndarray:
    if not HAVE_SCIPY or fs <= 0:
        return x.copy()

    nyq = 0.5 * fs

    if np.isscalar(cutoff):
        cutoff = float(cutoff)
        if cutoff >= nyq:
            return x.copy()
        wn = cutoff / nyq
    else:
        low, high = cutoff
        low = max(float(low), 1e-6)
        high = min(float(high), nyq * 0.999)
        if low >= high:
            return x.copy()
        wn = [low / nyq, high / nyq]

    b, a = butter(order, wn, btype=btype)
    return filtfilt(b, a, x)


def preprocess_signal_for_fft(
    y: np.ndarray,
    fs: float,
    use_highpass: bool = True,
    hp_cutoff: float = 5.0,
    hp_order: int = 4,
    use_median_baseline: bool = True,
    median_kernel_ratio: float = 0.03,
) -> np.ndarray:
    if y.size == 0:
        return y.astype(np.float64)

    out = y.astype(np.float64, copy=False)
    out = out - np.median(out)

    if use_median_baseline:
        out = robust_remove_baseline(out, kernel_ratio=median_kernel_ratio)

    if use_highpass and fs > 2.0 * hp_cutoff:
        out = butter_filter(out, fs, hp_cutoff, order=hp_order, btype="high")

    return out


def quadratic_peak_interp(freq: np.ndarray, spec: np.ndarray, idx: int):
    if idx <= 0 or idx >= len(spec) - 1:
        return float(freq[idx]), float(spec[idx])

    y1, y2, y3 = spec[idx - 1], spec[idx], spec[idx + 1]
    denom = (y1 - 2.0 * y2 + y3)
    if abs(denom) < 1e-15:
        return float(freq[idx]), float(spec[idx])

    delta = 0.5 * (y1 - y3) / denom
    df_step = freq[1] - freq[0]
    f_peak = freq[idx] + delta * df_step
    a_peak = y2 - 0.25 * (y1 - y3) * delta
    return float(f_peak), float(a_peak)


def compute_fft_spectrum(y: np.ndarray, fs: float, fmin: float, fmax: float, zero_pad_factor: int = 8):
    n = len(y)
    if n < 16 or fs <= 0:
        return None, None, None

    if HAVE_SCIPY:
        win = windows.hann(n)
    else:
        win = np.hanning(n)

    y_win = y * win
    nfft = int(2 ** np.ceil(np.log2(max(n, 1) * max(1, zero_pad_factor))))
    fft_vals = np.fft.rfft(y_win, n=nfft)
    freq = np.fft.rfftfreq(nfft, d=1.0 / fs)
    spec = (2.0 / np.sum(win)) * np.abs(fft_vals)

    if len(spec) > 0:
        spec[0] = 0.0

    mask = (freq >= fmin) & (freq <= fmax)
    if not np.any(mask):
        return freq, spec, None

    idx_local = int(np.argmax(spec[mask]))
    idx = np.where(mask)[0][idx_local]
    dom_freq, dom_amp = quadratic_peak_interp(freq, spec, idx)
    return freq, spec, (dom_freq, dom_amp)


def estimate_auto_band(
    freq: np.ndarray,
    spec: np.ndarray,
    search_band=(5.0, 120.0),
    min_width=8.0,
    max_width=24.0,
    rel_height=0.35,
    margin_hz=2.0,
):
    if freq is None or spec is None or len(freq) == 0 or len(spec) == 0:
        return None, None

    mask = (freq >= search_band[0]) & (freq <= search_band[1])
    if not np.any(mask):
        return None, None

    f = freq[mask]
    s = spec[mask]

    if len(s) < 5 or np.all(s <= 0):
        return None, None

    peak_idx_local = int(np.argmax(s))
    peak_freq = float(f[peak_idx_local])
    peak_amp = float(s[peak_idx_local])
    threshold = rel_height * peak_amp

    left = peak_idx_local
    while left > 0 and s[left] >= threshold:
        left -= 1

    right = peak_idx_local
    while right < len(s) - 1 and s[right] >= threshold:
        right += 1

    left_freq = float(f[left])
    right_freq = float(f[right])

    low = max(float(search_band[0]), left_freq - margin_hz)
    high = min(float(search_band[1]), right_freq + margin_hz)

    width = high - low
    if width < min_width:
        half = min_width / 2.0
        low = max(float(search_band[0]), peak_freq - half)
        high = min(float(search_band[1]), peak_freq + half)

    if (high - low) > max_width:
        half = max_width / 2.0
        low = max(float(search_band[0]), peak_freq - half)
        high = min(float(search_band[1]), peak_freq + half)

    if low >= high:
        half = min_width / 2.0
        low = max(float(search_band[0]), peak_freq - half)
        high = min(float(search_band[1]), peak_freq + half)

    return (float(low), float(high)), peak_freq


def compute_fft_summary(
    signal: np.ndarray,
    record_duration_sec: float,
    fft_fmin: float,
    fft_fmax: float,
    zero_pad_factor: int = 8,
    use_auto_band: bool = True,
):
    if signal.size < 16:
        return {
            "fftDominantFreq": None,
            "fftDominantAmp": None,
            "fftFreq": [],
            "fftSpec": [],
        }

    if record_duration_sec <= 0:
        record_duration_sec = 1.0

    fs = (len(signal) - 1) / record_duration_sec if len(signal) >= 2 else 0.0
    if fs <= 0:
        return {
            "fftDominantFreq": None,
            "fftDominantAmp": None,
            "fftFreq": [],
            "fftSpec": [],
        }

    y_proc = preprocess_signal_for_fft(signal, fs)

    search_fmin = min(fft_fmin, fft_fmax)
    search_fmax = max(fft_fmin, fft_fmax)

    freq0, spec0, _ = compute_fft_spectrum(
        y_proc,
        fs,
        search_fmin,
        search_fmax,
        zero_pad_factor=max(2, zero_pad_factor // 2),
    )

    if freq0 is None or spec0 is None:
        return {
            "fftDominantFreq": None,
            "fftDominantAmp": None,
            "fftFreq": [],
            "fftSpec": [],
        }

    final_band = (search_fmin, search_fmax)
    if use_auto_band:
        auto_band, _ = estimate_auto_band(
            freq0,
            spec0,
            search_band=(search_fmin, search_fmax),
            min_width=8.0,
            max_width=24.0,
            rel_height=0.35,
            margin_hz=2.0,
        )
        if auto_band is not None:
            final_band = auto_band

    freq, spec, dom = compute_fft_spectrum(
        y_proc,
        fs,
        final_band[0],
        final_band[1],
        zero_pad_factor=zero_pad_factor,
    )

    if freq is None or spec is None:
        return {
            "fftDominantFreq": None,
            "fftDominantAmp": None,
            "fftFreq": [],
            "fftSpec": [],
        }

    band_mask = (freq >= search_fmin) & (freq <= search_fmax)
    freq_band = freq[band_mask]
    spec_band = spec[band_mask]

    return {
        "fftDominantFreq": None if dom is None else float(dom[0]),
        "fftDominantAmp": None if dom is None else float(dom[1]),
        "fftFreq": freq_band.tolist(),
        "fftSpec": spec_band.tolist(),
    }


def _safe_view_indices(start, end_exclusive, total_count: int):
    if total_count <= 0:
        return 0, 0
    start = 0 if start is None else int(start)
    end_exclusive = total_count if end_exclusive is None else int(end_exclusive)
    start = max(0, min(start, total_count - 1))
    end_exclusive = max(start + 1, min(end_exclusive, total_count))
    return start, end_exclusive


def _fig_to_base64(fig) -> str:
    buf = BytesIO()
    fig.subplots_adjust(left=0.07, right=0.99, top=0.92, bottom=0.14)
    fig.savefig(buf, format="png", dpi=170, bbox_inches="tight", pad_inches=0.03)
    plt.close(fig)
    return base64.b64encode(buf.getvalue()).decode("ascii")


def render_signal_plot(raw: np.ndarray, filtered: np.ndarray, start, end_exclusive, channel: int, startup_trim_enabled: bool = False, startup_trim_samples: int = 0):
    total = max(raw.size, filtered.size)
    if total < 2:
        raise RuntimeError("Not enough signal data to render plot")

    start, end_exclusive = _safe_view_indices(start, end_exclusive, total)
    raw_end = min(end_exclusive, raw.size)
    filt_end = min(end_exclusive, filtered.size)

    raw_slice = raw[start:raw_end]
    filt_slice = filtered[start:filt_end]
    x_raw = np.arange(start, start + raw_slice.size)
    x_filt = np.arange(start, start + filt_slice.size)

    fig, ax = plt.subplots(figsize=(12.8, 5.6))
    if raw_slice.size:
        ax.plot(x_raw, raw_slice, linewidth=1.0, label="Raw")
    peak_count = 0
    if filt_slice.size:
        ax.plot(x_filt, filt_slice, linewidth=1.2, label="Filtered")
        trim_idx = int(max(0, startup_trim_samples)) if startup_trim_enabled else 0
        if trim_idx > 0 and x_filt.size:
            trim_end = min(trim_idx, filt_slice.size)
            if trim_end > 0:
                ax.axvspan(float(x_filt[0]), float(x_filt[trim_end - 1]), alpha=0.08, label="Startup trim")
        trimmed_slice = filt_slice[trim_idx:] if trim_idx < filt_slice.size else np.array([], dtype=np.float64)
        peak_idx = detect_peaks_on_filtered(trimmed_slice) + trim_idx if trimmed_slice.size else np.array([], dtype=np.int64)
        peak_count = int(len(peak_idx))
        if peak_idx.size:
            peak_x = x_filt[peak_idx]
            peak_y = filt_slice[peak_idx]
            ax.scatter(peak_x, peak_y, s=18, label=f"Filtered peaks ({peak_count})")
            y_span = float(np.max(filt_slice) - np.min(filt_slice)) if filt_slice.size else 0.0
            y_offset = max(y_span * 0.04, 0.02)
            for idx, (px, py) in enumerate(zip(peak_x, peak_y), start=1):
                ax.annotate(
                    str(idx),
                    xy=(float(px), float(py)),
                    xytext=(float(px), float(py + y_offset)),
                    textcoords="data",
                    ha="center",
                    va="bottom",
                    fontsize=7,
                    alpha=0.85,
                )
    ax.set_title(f"Signal Plot - Repetition {channel}")
    ax.set_xlabel("Sample")
    ax.set_ylabel("Amplitude")
    ax.grid(True, alpha=0.25)
    if raw_slice.size or filt_slice.size:
        ax.legend(loc="upper left")
    return _fig_to_base64(fig)


def render_fft_plot(freq: np.ndarray, spec: np.ndarray, start, end_exclusive, channel: int):
    total = min(freq.size, spec.size)
    if total < 2:
        raise RuntimeError("Not enough FFT data to render plot")

    start, end_exclusive = _safe_view_indices(start, end_exclusive, total)
    freq_slice = freq[start:end_exclusive]
    spec_slice = spec[start:end_exclusive]

    fig, ax = plt.subplots(figsize=(12.8, 5.2))
    ax.plot(freq_slice, spec_slice, linewidth=1.0)
    if spec_slice.size:
        peak_idx = int(np.argmax(spec_slice))
        peak_freq = float(freq_slice[peak_idx])
        peak_amp = float(spec_slice[peak_idx])
        ax.scatter([peak_freq], [peak_amp], s=18)

        y_span = float(np.max(spec_slice) - np.min(spec_slice)) if spec_slice.size else 0.0
        y_offset = max(y_span * 0.06, peak_amp * 0.04, 0.02)
        label_y = peak_amp + y_offset
        ax.annotate(
            f"{int(round(peak_freq))} Hz",
            xy=(peak_freq, peak_amp),
            xytext=(peak_freq, label_y),
            textcoords="data",
            ha="center",
            va="bottom",
            fontsize=10,
            fontweight="bold",
            arrowprops={"arrowstyle": "->", "lw": 0.8, "alpha": 0.65},
            bbox={"boxstyle": "round,pad=0.22", "fc": "white", "ec": "0.75", "alpha": 0.92},
        )
    ax.set_title(f"FFT Plot - Repetition {channel}")
    ax.set_xlabel("Frequency (Hz)")
    ax.set_ylabel("Amplitude")
    ax.grid(True, alpha=0.25)
    return _fig_to_base64(fig)


def handle_render_plot(req: dict) -> dict:
    ch = int(req["channel"])
    params_version = int(req.get("paramsVersion", 0))
    plot_kind = str(req.get("plotKind", "signal"))
    start = req.get("viewStartIndex")
    end_exclusive = req.get("viewEndExclusive")
    startup_trim_enabled = bool(req.get("startupTrimEnabled", False))
    startup_trim_samples = int(req.get("startupTrimSamples", 0))

    if plot_kind == "signal":
        raw = np.array(req.get("raw", []), dtype=np.float64)
        filtered = np.array(req.get("filtered", []), dtype=np.float64)
        image_base64 = render_signal_plot(raw, filtered, start, end_exclusive, ch, startup_trim_enabled=startup_trim_enabled, startup_trim_samples=startup_trim_samples)
    elif plot_kind == "fft":
        freq = np.array(req.get("fftFreq", []), dtype=np.float64)
        spec = np.array(req.get("fftSpec", []), dtype=np.float64)
        image_base64 = render_fft_plot(freq, spec, start, end_exclusive, ch)
    else:
        raise RuntimeError(f"Unknown plot kind: {plot_kind}")

    return {
        "ok": True,
        "channel": ch,
        "paramsVersion": params_version,
        "plotKind": plot_kind,
        "imageBase64": image_base64,
    }


# =========================================================
# MAIN HANDLE
# =========================================================
def handle(req: dict) -> dict:
    ch = int(req["channel"])
    seq_start = int(req["seqStart"])
    params = req["params"]

    version = int(params["version"])
    ftype = str(params["type"])

    tail = np.array(req.get("tail", []), dtype=np.int64)
    chunk = np.array(req.get("chunk", []), dtype=np.int64)

    record_duration_sec = float(params.get("recordDurationSec", 1.0))
    fft_enabled = bool(params.get("fftEnabled", True))
    fft_fmin = float(params.get("fftFmin", 5.0))
    fft_fmax = float(params.get("fftFmax", 120.0))
    fft_zero_pad_factor = int(params.get("fftZeroPadFactor", 8))
    fft_use_auto_band = bool(params.get("fftUseAutoBand", True))
    startup_trim_enabled = bool(params.get("startupTrimEnabled", False))
    startup_trim_samples = int(params.get("startupTrimSamples", 0))

    if chunk.size == 0:
        return {
            "ok": True,
            "channel": ch,
            "seqStart": seq_start,
            "paramsVersion": version,
            "filtered": [],
            "peakCount": 0,
            "fftDominantFreq": None,
            "fftDominantAmp": None,
            "fftFreq": [],
            "fftSpec": [],
        }

    combined_raw = np.concatenate([tail, chunk]).astype(np.float64, copy=False)

    if ftype == "SG":
        window = int(params["sgWindow"])
        poly = int(params["sgOrder"])
        combined_filtered = sg_process_full(combined_raw, window, poly)
        y_chunk = combined_filtered[len(tail):]
    elif ftype == "Kalman":
        q = float(params["kalmanQ"])
        r = float(params["kalmanR"])
        y_chunk = kalman_process(ch, chunk, q, r, version)
    else:
        y_chunk = chunk.astype(np.float64, copy=False)

    # Fringe count is based on the full filtered history for the current
    # repetition/version, so Python remains the single source of truth.
    # FFT is still computed from raw data so it is not affected by the
    # selected filter.
    analysis_signal = combined_raw

    peak_count = update_global_peak_count(ch, version, y_chunk, startup_trim_enabled=startup_trim_enabled, startup_trim_samples=startup_trim_samples)

    fft_result = {
        "fftDominantFreq": None,
        "fftDominantAmp": None,
        "fftFreq": [],
        "fftSpec": [],
    }

    if fft_enabled:
        fft_signal = analysis_signal
        if startup_trim_enabled:
            raw_history = update_raw_history(ch, version, chunk.astype(np.float64, copy=False))
            trim_idx = int(max(0, startup_trim_samples))
            fft_signal = raw_history[trim_idx:] if trim_idx < raw_history.size else np.array([], dtype=np.float64)
        fft_result = compute_fft_summary(
            signal=fft_signal,
            record_duration_sec=record_duration_sec,
            fft_fmin=fft_fmin,
            fft_fmax=fft_fmax,
            zero_pad_factor=fft_zero_pad_factor,
            use_auto_band=fft_use_auto_band,
        )

    return {
        "ok": True,
        "channel": ch,
        "seqStart": seq_start,
        "paramsVersion": version,
        "filtered": y_chunk.tolist(),
        "peakCount": int(peak_count),
        "fftDominantFreq": fft_result["fftDominantFreq"],
        "fftDominantAmp": fft_result["fftDominantAmp"],
        "fftFreq": fft_result["fftFreq"],
        "fftSpec": fft_result["fftSpec"],
    }


# =========================================================
# MAIN LOOP
# =========================================================
def main():
    sys.stdout.write("READY\n")
    sys.stdout.flush()

    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue

        try:
            req = json.loads(line)

            if req.get("type") == "ping":
                sys.stdout.write(json.dumps({
                    "ok": True,
                    "type": "pong",
                    "python": sys.executable,
                    "scipy": HAVE_SCIPY,
                }) + "\n")
                sys.stdout.flush()
                continue

            if req.get("type") == "render_plot":
                resp = handle_render_plot(req)
            else:
                resp = handle(req)
            sys.stdout.write(json.dumps(resp) + "\n")
            sys.stdout.flush()

        except Exception as e:
            sys.stdout.write(json.dumps({
                "ok": False,
                "error": str(e),
                "trace": traceback.format_exc(limit=6),
            }) + "\n")
            sys.stdout.flush()


if __name__ == "__main__":
    main()