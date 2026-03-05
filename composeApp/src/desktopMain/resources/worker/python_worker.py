import sys, json, traceback
import numpy as np

# Optional SciPy
try:
    from scipy.signal import savgol_filter
    HAVE_SCIPY = True
except Exception:
    HAVE_SCIPY = False

# Kalman state per channel
_kalman_state = {}  # ch -> (x, P)
_last_version = {}  # ch -> paramsVersion

def kalman_process(ch: int, chunk: np.ndarray, q: float, r: float, version: int) -> np.ndarray:
    # Reset state if version changed (safe default)
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

def sg_process(tail: np.ndarray, chunk: np.ndarray, window: int, poly: int) -> np.ndarray:
    if not HAVE_SCIPY:
        raise RuntimeError("SciPy not available in worker. Bundle SciPy or use Kalman.")
    # sanitize
    if window % 2 == 0:
        window += 1
    window = max(3, min(window, 301))
    poly = max(2, min(poly, min(10, window - 1)))

    x = np.concatenate([tail, chunk]).astype(np.float64, copy=False)
    # mode='interp' biasanya paling stabil untuk ujung
    y = savgol_filter(x, window_length=window, polyorder=poly, mode="interp")
    # Return only aligned to chunk (drop tail overlap)
    return y[len(tail):]

def handle(req: dict) -> dict:
    # required
    ch = int(req["channel"])
    seq_start = int(req["seqStart"])
    params = req["params"]
    version = int(params["version"])
    ftype = str(params["type"])

    tail = np.array(req.get("tail", []), dtype=np.int64)
    chunk = np.array(req.get("chunk", []), dtype=np.int64)

    if chunk.size == 0:
        return {
            "ok": True,
            "channel": ch,
            "seqStart": seq_start,
            "paramsVersion": version,
            "filtered": [],
        }

    if ftype == "SG":
        window = int(params["sgWindow"])
        poly = int(params["sgOrder"])
        y = sg_process(tail, chunk, window, poly)
    elif ftype == "Kalman":
        q = float(params["kalmanQ"])
        r = float(params["kalmanR"])
        y = kalman_process(ch, chunk, q, r, version)
    else:
        y = chunk.astype(np.float64, copy=False)

    return {
        "ok": True,
        "channel": ch,
        "seqStart": seq_start,
        "paramsVersion": version,
        "filtered": y.tolist(),
    }

def main():
    # simple handshake
    sys.stdout.write("READY\n")
    sys.stdout.flush()

    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue
        try:
            req = json.loads(line)
            if req.get("type") == "ping":
                sys.stdout.write(json.dumps({"ok": True, "type": "pong"}) + "\n")
                sys.stdout.flush()
                continue

            resp = handle(req)
            sys.stdout.write(json.dumps(resp) + "\n")
            sys.stdout.flush()
        except Exception as e:
            sys.stdout.write(json.dumps({
                "ok": False,
                "error": str(e),
                "trace": traceback.format_exc(limit=3),
            }) + "\n")
            sys.stdout.flush()

if __name__ == "__main__":
    main()