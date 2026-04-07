import sys
import json
import traceback
import base64
from io import BytesIO
import numpy as np
import matplotlib
matplotlib.use('Agg')
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
# GLOBAL STATE
# =========================================================
_kalman_state = {}          # ch -> (x, P) for streaming fallback only
_last_version = {}          # ch -> paramsVersion
_raw_history = {}           # ch -> {"version": int, "values": list[float]}
_params_history = {}        # ch -> {"version": int, "params": dict}


# =========================================================
# FILTER CORE
# =========================================================
def _reset_channel_if_needed(ch: int, version: int):
    if _last_version.get(ch) != version:
        _kalman_state.pop(ch, None)
        _last_version[ch] = version


def kalman_process(ch: int, chunk: np.ndarray, q: float, r: float, version: int) -> np.ndarray:
    _reset_channel_if_needed(ch, version)

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


def _make_odd(x: int) -> int:
    x = int(x)
    if x < 3:
        x = 3
    if x % 2 == 0:
        x += 1
    return x


def _sg_fallback_numpy(x: np.ndarray, window: int, poly: int) -> np.ndarray:
    x = x.astype(np.float64, copy=False)
    n = x.size
    if n < 3:
        return x.copy()

    window = _make_odd(window)
    if window > n:
        window = n if n % 2 == 1 else n - 1
    if window < 3:
        return x.copy()

    poly = min(int(poly), window - 1)
    if poly < 1:
        poly = 1

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


def safe_savgol(y: np.ndarray, window_length: int, polyorder: int) -> np.ndarray:
    y = np.asarray(y, dtype=np.float64)
    n = len(y)
    wl = _make_odd(window_length)
    if wl > n:
        wl = n if n % 2 == 1 else n - 1
    if wl < 3:
        return y.copy()

    po = min(int(polyorder), wl - 1)
    if po < 1:
        po = 1

    if HAVE_SCIPY:
        return savgol_filter(y, window_length=wl, polyorder=po, mode='interp')
    return _sg_fallback_numpy(y, wl, po)


def kalman_1d(z: np.ndarray, q: float = 0.01, r: float = 5.0) -> np.ndarray:
    z = np.asarray(z, dtype=np.float64)
    if z.size == 0:
        return z.copy()

    xhat = np.zeros_like(z)
    P = np.zeros_like(z)
    xhat[0] = z[0]
    P[0] = 1.0

    for k in range(1, len(z)):
        xhat_minus = xhat[k - 1]
        P_minus = P[k - 1] + q
        K = P_minus / (P_minus + r)
        xhat[k] = xhat_minus + K * (z[k] - xhat_minus)
        P[k] = (1.0 - K) * P_minus

    return xhat


def filter_signal_full(raw: np.ndarray, params: dict) -> np.ndarray:
    raw = np.asarray(raw, dtype=np.float64)
    ftype = str(params.get('type', 'SG'))
    if ftype == 'SG':
        return safe_savgol(raw, int(params.get('sgWindow', 83)), int(params.get('sgOrder', 5)))
    if ftype == 'Kalman':
        return kalman_1d(raw, q=float(params.get('kalmanQ', 0.01)), r=float(params.get('kalmanR', 5.0)))
    return raw.copy()


# =========================================================
# GUI-LIKE PEAK COUNT
# =========================================================
def _fallback_peaks_absolute(values: np.ndarray, prominence: float, distance: int) -> np.ndarray:
    values = np.asarray(values, dtype=np.float64)
    n = values.size
    if n < 3:
        return np.array([], dtype=np.int64)

    candidates = []
    for i in range(1, n - 1):
        prev_v = values[i - 1]
        curr_v = values[i]
        next_v = values[i + 1]
        if ((curr_v > prev_v and curr_v >= next_v) or (curr_v >= prev_v and curr_v > next_v)):
            left_min = float(np.min(values[:i])) if i > 0 else curr_v
            right_min = float(np.min(values[i + 1:])) if i + 1 < n else curr_v
            prom = float(curr_v - max(left_min, right_min))
            if prom >= prominence:
                candidates.append((i, curr_v))

    if not candidates:
        return np.array([], dtype=np.int64)

    candidates.sort(key=lambda t: t[1], reverse=True)
    kept = []
    for idx, _ in candidates:
        if all(abs(idx - prev) >= distance for prev in kept):
            kept.append(idx)
    kept.sort()
    return np.asarray(kept, dtype=np.int64)


def detect_peaks_gui_like(values: np.ndarray, prominence_x10: int = 22, distance: int = 81):
    values = np.asarray(values, dtype=np.float64)
    if values.size < 3:
        return np.array([], dtype=np.int64)

    prominence = max(0.0, float(prominence_x10) / 10.0)
    distance = max(1, int(distance))

    if HAVE_SCIPY:
        peaks, _ = find_peaks(values, prominence=prominence, distance=distance)
        return peaks.astype(np.int64, copy=False)
    return _fallback_peaks_absolute(values, prominence=prominence, distance=distance)


# =========================================================
# HISTORY / PARAMS
# =========================================================
def _store_params(ch: int, version: int, params: dict):
    _params_history[ch] = {'version': int(version), 'params': dict(params)}


def _get_params_for_channel(ch: int, version: int) -> dict | None:
    state = _params_history.get(ch)
    if state is None:
        return None
    if int(state.get('version', -1)) != int(version):
        return None
    return dict(state.get('params', {}))


def append_raw_history(ch: int, version: int, y_chunk: np.ndarray):
    state = _raw_history.get(ch)
    if state is None or int(state.get('version', -1)) != int(version):
        state = {'version': int(version), 'values': []}
        _raw_history[ch] = state
    if y_chunk.size:
        state['values'].extend(np.asarray(y_chunk, dtype=np.float64).tolist())


def get_raw_history(ch: int, version: int) -> np.ndarray:
    state = _raw_history.get(ch)
    if state is None or int(state.get('version', -1)) != int(version):
        return np.array([], dtype=np.float64)
    return np.asarray(state.get('values', []), dtype=np.float64)


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


def butter_filter(x: np.ndarray, fs: float, cutoff, order: int = 4, btype: str = 'high') -> np.ndarray:
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
        out = butter_filter(out, fs, hp_cutoff, order=hp_order, btype='high')

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


def estimate_auto_band(freq: np.ndarray, spec: np.ndarray, search_band=(5.0, 120.0), min_width=8.0, max_width=24.0, rel_height=0.35, margin_hz=2.0):
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

    low = max(float(search_band[0]), float(f[left]) - margin_hz)
    high = min(float(search_band[1]), float(f[right]) + margin_hz)

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


def compute_fft_summary(signal: np.ndarray, record_duration_sec: float, fft_fmin: float, fft_fmax: float, zero_pad_factor: int = 8, use_auto_band: bool = True):
    if signal.size < 16:
        return {'fftDominantFreq': None, 'fftDominantAmp': None, 'fftFreq': [], 'fftSpec': []}

    if record_duration_sec <= 0:
        record_duration_sec = 1.0

    fs = (len(signal) - 1) / record_duration_sec if len(signal) >= 2 else 0.0
    if fs <= 0:
        return {'fftDominantFreq': None, 'fftDominantAmp': None, 'fftFreq': [], 'fftSpec': []}

    y_proc = preprocess_signal_for_fft(signal, fs)
    search_fmin = min(fft_fmin, fft_fmax)
    search_fmax = max(fft_fmin, fft_fmax)

    freq0, spec0, _ = compute_fft_spectrum(y_proc, fs, search_fmin, search_fmax, zero_pad_factor=max(2, zero_pad_factor // 2))
    if freq0 is None or spec0 is None:
        return {'fftDominantFreq': None, 'fftDominantAmp': None, 'fftFreq': [], 'fftSpec': []}

    final_band = (search_fmin, search_fmax)
    if use_auto_band:
        auto_band, _ = estimate_auto_band(freq0, spec0, search_band=(search_fmin, search_fmax), min_width=8.0, max_width=24.0, rel_height=0.35, margin_hz=2.0)
        if auto_band is not None:
            final_band = auto_band

    freq, spec, dom = compute_fft_spectrum(y_proc, fs, final_band[0], final_band[1], zero_pad_factor=zero_pad_factor)
    if freq is None or spec is None:
        return {'fftDominantFreq': None, 'fftDominantAmp': None, 'fftFreq': [], 'fftSpec': []}

    band_mask = (freq >= search_fmin) & (freq <= search_fmax)
    freq_band = freq[band_mask]
    spec_band = spec[band_mask]

    return {
        'fftDominantFreq': None if dom is None else float(dom[0]),
        'fftDominantAmp': None if dom is None else float(dom[1]),
        'fftFreq': freq_band.tolist(),
        'fftSpec': spec_band.tolist(),
    }


# =========================================================
# PLOT HELPERS
# =========================================================
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
    fig.savefig(buf, format='png', dpi=170, bbox_inches='tight', pad_inches=0.03)
    plt.close(fig)
    return base64.b64encode(buf.getvalue()).decode('ascii')


def render_signal_plot(raw: np.ndarray, filtered: np.ndarray, start, end_exclusive, channel: int, peak_prominence_x10: int = 22, peak_distance: int = 81):
    total = max(raw.size, filtered.size)
    if total < 2:
        raise RuntimeError('Not enough signal data to render plot')

    start, end_exclusive = _safe_view_indices(start, end_exclusive, total)
    raw_end = min(end_exclusive, raw.size)
    filt_end = min(end_exclusive, filtered.size)

    raw_slice = raw[start:raw_end]
    filt_slice = filtered[start:filt_end]
    x_raw = np.arange(start, start + raw_slice.size)
    x_filt = np.arange(start, start + filt_slice.size)

    peak_idx_local = detect_peaks_gui_like(filt_slice, prominence_x10=peak_prominence_x10, distance=peak_distance)
    peak_count = int(len(peak_idx_local))

    fig, ax = plt.subplots(figsize=(12.8, 5.6))
    if raw_slice.size:
        ax.plot(x_raw, raw_slice, linewidth=1.0, label='Raw')
    if filt_slice.size:
        ax.plot(x_filt, filt_slice, linewidth=1.2, label='Filtered')
        if peak_idx_local.size:
            peak_x = x_filt[peak_idx_local]
            peak_y = filt_slice[peak_idx_local]
            ax.scatter(peak_x, peak_y, s=18, label=f'Filtered peaks ({peak_count})')
            y_span = float(np.max(filt_slice) - np.min(filt_slice)) if filt_slice.size else 0.0
            y_offset = max(y_span * 0.04, 0.02)
            for idx, (px, py) in enumerate(zip(peak_x, peak_y), start=1):
                ax.annotate(str(idx), xy=(float(px), float(py)), xytext=(float(px), float(py + y_offset)), textcoords='data', ha='center', va='bottom', fontsize=7, alpha=0.85)
    ax.set_title(f'Signal Plot - Repetition {channel}')
    ax.set_xlabel('Sample')
    ax.set_ylabel('Amplitude')
    ax.grid(True, alpha=0.25)
    if raw_slice.size or filt_slice.size:
        ax.legend(loc='upper left')
    return _fig_to_base64(fig)


def render_fft_plot(freq: np.ndarray, spec: np.ndarray, start, end_exclusive, channel: int):
    total = min(freq.size, spec.size)
    if total < 2:
        raise RuntimeError('Not enough FFT data to render plot')

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
        ax.annotate(f'{int(round(peak_freq))} Hz', xy=(peak_freq, peak_amp), xytext=(peak_freq, label_y), textcoords='data', ha='center', va='bottom', fontsize=10, fontweight='bold', arrowprops={'arrowstyle': '->', 'lw': 0.8, 'alpha': 0.65}, bbox={'boxstyle': 'round,pad=0.22', 'fc': 'white', 'ec': '0.75', 'alpha': 0.92})
    ax.set_title(f'FFT Plot - Repetition {channel}')
    ax.set_xlabel('Frequency (Hz)')
    ax.set_ylabel('Amplitude')
    ax.grid(True, alpha=0.25)
    return _fig_to_base64(fig)


def handle_render_plot(req: dict) -> dict:
    ch = int(req['channel'])
    params_version = int(req.get('paramsVersion', 0))
    plot_kind = str(req.get('plotKind', 'signal'))
    start = req.get('viewStartIndex')
    end_exclusive = req.get('viewEndExclusive')

    if plot_kind == 'signal':
        raw = np.array(req.get('raw', []), dtype=np.float64)
        params = _get_params_for_channel(ch, params_version)
        if params is not None and raw.size:
            filtered = filter_signal_full(raw, params)
            peak_prominence_x10 = int(params.get('peakProminenceX10', 22))
            peak_distance = int(params.get('peakDistance', 81))
        else:
            filtered = np.array(req.get('filtered', []), dtype=np.float64)
            peak_prominence_x10 = 22
            peak_distance = 81
        image_base64 = render_signal_plot(raw, filtered, start, end_exclusive, ch, peak_prominence_x10=peak_prominence_x10, peak_distance=peak_distance)
    elif plot_kind == 'fft':
        freq = np.array(req.get('fftFreq', []), dtype=np.float64)
        spec = np.array(req.get('fftSpec', []), dtype=np.float64)
        image_base64 = render_fft_plot(freq, spec, start, end_exclusive, ch)
    else:
        raise RuntimeError(f'Unknown plot kind: {plot_kind}')

    return {
        'ok': True,
        'channel': ch,
        'paramsVersion': params_version,
        'plotKind': plot_kind,
        'imageBase64': image_base64,
    }


# =========================================================
# MAIN HANDLE
# =========================================================
def handle(req: dict) -> dict:
    ch = int(req['channel'])
    seq_start = int(req['seqStart'])
    params = dict(req['params'])

    version = int(params['version'])
    record_duration_sec = float(params.get('recordDurationSec', 1.0))
    fft_enabled = bool(params.get('fftEnabled', True))
    fft_fmin = float(params.get('fftFmin', 5.0))
    fft_fmax = float(params.get('fftFmax', 120.0))
    fft_zero_pad_factor = int(params.get('fftZeroPadFactor', 8))
    fft_use_auto_band = bool(params.get('fftUseAutoBand', True))

    tail = np.array(req.get('tail', []), dtype=np.int64)
    chunk = np.array(req.get('chunk', []), dtype=np.int64)

    # Keep Kotlin compatibility: use defaults when Kotlin does not send peak params.
    params.setdefault('peakProminenceX10', 22)
    params.setdefault('peakDistance', 81)

    _store_params(ch, version, params)

    if chunk.size == 0:
        return {
            'ok': True,
            'channel': ch,
            'seqStart': seq_start,
            'paramsVersion': version,
            'filtered': [],
            'peakCount': 0,
            'fftDominantFreq': None,
            'fftDominantAmp': None,
            'fftFreq': [],
            'fftSpec': [],
        }

    append_raw_history(ch, version, chunk.astype(np.float64, copy=False))
    full_raw = get_raw_history(ch, version)
    full_filtered = filter_signal_full(full_raw, params)
    y_chunk = full_filtered[-len(chunk):] if len(chunk) <= len(full_filtered) else full_filtered.copy()

    peak_idx = detect_peaks_gui_like(
        full_filtered,
        prominence_x10=int(params.get('peakProminenceX10', 22)),
        distance=int(params.get('peakDistance', 81)),
    )
    peak_count = int(len(peak_idx))

    analysis_signal = full_raw
    fft_result = {
        'fftDominantFreq': None,
        'fftDominantAmp': None,
        'fftFreq': [],
        'fftSpec': [],
    }
    if fft_enabled:
        fft_result = compute_fft_summary(
            signal=analysis_signal,
            record_duration_sec=record_duration_sec,
            fft_fmin=fft_fmin,
            fft_fmax=fft_fmax,
            zero_pad_factor=fft_zero_pad_factor,
            use_auto_band=fft_use_auto_band,
        )

    return {
        'ok': True,
        'channel': ch,
        'seqStart': seq_start,
        'paramsVersion': version,
        'filtered': y_chunk.tolist(),
        'peakCount': peak_count,
        'fftDominantFreq': fft_result['fftDominantFreq'],
        'fftDominantAmp': fft_result['fftDominantAmp'],
        'fftFreq': fft_result['fftFreq'],
        'fftSpec': fft_result['fftSpec'],
    }


# =========================================================
# MAIN LOOP
# =========================================================
def main():
    sys.stdout.write('READY\n')
    sys.stdout.flush()

    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue

        try:
            req = json.loads(line)
            if req.get('type') == 'ping':
                sys.stdout.write(json.dumps({
                    'ok': True,
                    'type': 'pong',
                    'python': sys.executable,
                    'scipy': HAVE_SCIPY,
                }) + '\n')
                sys.stdout.flush()
                continue

            if req.get('type') == 'render_plot':
                resp = handle_render_plot(req)
            else:
                resp = handle(req)
            sys.stdout.write(json.dumps(resp) + '\n')
            sys.stdout.flush()

        except Exception as e:
            sys.stdout.write(json.dumps({
                'ok': False,
                'error': str(e),
                'trace': traceback.format_exc(limit=6),
            }) + '\n')
            sys.stdout.flush()


if __name__ == '__main__':
    main()
