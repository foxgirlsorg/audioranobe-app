"""Glue between the Kotlin narrator and the Silero text front end (frontend.py, downloaded with the models).

frontend.py awaits two neural callbacks; here they are plain Java callbacks (`.call(json) -> json`) run to completion.
"""
import asyncio
import sys

_fe = None


def load(directory):
    global _fe
    if directory not in sys.path:
        sys.path.insert(0, directory)
    import frontend
    _fe = frontend
    return frontend.load_data(directory)


def pieces(text):
    return _fe.split_text(_fe.normalize(text))


def prepare(piece, data, accent_cb, homo_cb):
    async def accent(req):
        return accent_cb.call(req)

    async def homo(req):
        return homo_cb.call(req)

    seq, type_ids = asyncio.run(_fe.prepare(piece, data, accent, homo))
    return list(seq), list(type_ids)
