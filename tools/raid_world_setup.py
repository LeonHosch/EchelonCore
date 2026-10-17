"""Inspect or apply the raid script's saved-world portal and pocket attachment.

Run with --apply only while the Minecraft world is closed. The original NBT
files are backed up beside them before replacement.
"""

from __future__ import annotations

import argparse
import copy
import gzip
import io
import os
from pathlib import Path
import shutil
import struct
from dataclasses import dataclass


@dataclass
class Tag:
    kind: int
    value: object


def read_exact(stream: io.BytesIO, count: int) -> bytes:
    data = stream.read(count)
    if len(data) != count:
        raise ValueError("Truncated NBT data")
    return data


def read_number(stream: io.BytesIO, fmt: str):
    return struct.unpack(">" + fmt, read_exact(stream, struct.calcsize(fmt)))[0]


def read_string(stream: io.BytesIO) -> str:
    return read_exact(stream, read_number(stream, "H")).decode("utf-8")


def read_payload(stream: io.BytesIO, kind: int):
    if kind in (1, 2, 3, 4, 5, 6):
        return read_number(stream, {1: "b", 2: "h", 3: "i", 4: "q", 5: "f", 6: "d"}[kind])
    if kind == 7:
        return read_exact(stream, read_number(stream, "i"))
    if kind == 8:
        return read_string(stream)
    if kind == 9:
        element = read_number(stream, "B")
        return element, [read_payload(stream, element) for _ in range(read_number(stream, "i"))]
    if kind == 10:
        values = {}
        while (child_kind := read_number(stream, "B")) != 0:
            child_name = read_string(stream)
            values[child_name] = Tag(child_kind, read_payload(stream, child_kind))
        return values
    if kind in (11, 12):
        fmt = "i" if kind == 11 else "q"
        return [read_number(stream, fmt) for _ in range(read_number(stream, "i"))]
    raise ValueError(f"Unsupported NBT tag {kind}")


def write_number(stream: io.BytesIO, fmt: str, value) -> None:
    stream.write(struct.pack(">" + fmt, value))


def write_string(stream: io.BytesIO, value: str) -> None:
    data = value.encode("utf-8")
    write_number(stream, "H", len(data))
    stream.write(data)


def write_payload(stream: io.BytesIO, tag: Tag) -> None:
    kind, value = tag.kind, tag.value
    if kind in (1, 2, 3, 4, 5, 6):
        write_number(stream, {1: "b", 2: "h", 3: "i", 4: "q", 5: "f", 6: "d"}[kind], value)
    elif kind == 7:
        write_number(stream, "i", len(value))
        stream.write(value)
    elif kind == 8:
        write_string(stream, value)
    elif kind == 9:
        element, items = value
        write_number(stream, "B", element)
        write_number(stream, "i", len(items))
        for item in items:
            write_payload(stream, Tag(element, item))
    elif kind == 10:
        for name, child in value.items():
            write_number(stream, "B", child.kind)
            write_string(stream, name)
            write_payload(stream, child)
        write_number(stream, "B", 0)
    elif kind in (11, 12):
        write_number(stream, "i", len(value))
        for item in value:
            write_number(stream, "i" if kind == 11 else "q", item)
    else:
        raise ValueError(f"Unsupported NBT tag {kind}")


def decode(path: Path) -> tuple[str, Tag]:
    stream = io.BytesIO(gzip.decompress(path.read_bytes()))
    kind = read_number(stream, "B")
    name = read_string(stream)
    root = Tag(kind, read_payload(stream, kind))
    if stream.read(1):
        raise ValueError(f"Trailing NBT data in {path}")
    return name, root


def encode(name: str, root: Tag) -> bytes:
    stream = io.BytesIO()
    write_number(stream, "B", root.kind)
    write_string(stream, name)
    write_payload(stream, root)
    return gzip.compress(stream.getvalue(), mtime=0)


def plain(tag: Tag):
    if tag.kind == 10:
        return {name: plain(child) for name, child in tag.value.items()}
    if tag.kind == 9:
        element, values = tag.value
        return [plain(Tag(element, value)) for value in values]
    return tag.value


def string(value: str) -> Tag:
    return Tag(8, value)


def number(value: float) -> Tag:
    return Tag(6, float(value))


def compound(value: dict[str, Tag]) -> Tag:
    return Tag(10, value)


def save_pair(items: list[tuple[Path, str, Tag]]) -> None:
    prepared = []
    for path, name, root in items:
        original = path.read_bytes()
        backup = path.with_name(path.name + ".pre-raidscript.bak")
        if backup.exists() and backup.read_bytes() != original:
            raise RuntimeError(f"Backup differs from current world data: {backup}")
        encoded = encode(name, root)
        temporary = path.with_name(path.name + ".raidsetup.tmp")
        temporary.write_bytes(encoded)
        if decode(temporary) != (name, root):
            temporary.unlink()
            raise RuntimeError(f"NBT verification failed for {path}")
        prepared.append((path, backup, temporary, original))
    try:
        for path, backup, temporary, original in prepared:
            if not backup.exists():
                backup.write_bytes(original)
            os.replace(temporary, path)
    except Exception:
        for path, backup, _, _ in prepared:
            if backup.exists():
                shutil.copy2(backup, path)
        raise
    finally:
        for _, _, temporary, _ in prepared:
            temporary.unlink(missing_ok=True)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("world", type=Path)
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()
    data_dir = args.world / "dimensions/minecraft/overworld/data/crazy-world-progression"
    portal_path = data_dir / "portals.dat"
    pocket_path = data_dir / "pocket_editors.dat"
    portal_name, portal_root = decode(portal_path)
    pocket_name, pocket_root = decode(pocket_path)
    portal_data = portal_root.value["data"].value
    pocket_data = pocket_root.value["data"].value
    portals = portal_data["portals"].value
    pockets = pocket_data["pockets"].value
    scripts = pocket_data.get("mob_scripts", Tag(10, {})).value
    print("Portals:")
    for key, portal in portals.items():
        print(key, plain(portal))
    print("Pockets:", list(pockets))
    print("Attached scripts:", {key: plain(value) for key, value in scripts.items()})
    if not args.apply:
        return
    if "crazy-world-progression:raidtestpocket" not in pockets:
        raise RuntimeError("The raidtest pocket is not in this world")
    gate_id = "cwp:gate"
    if gate_id in portals:
        raise RuntimeError("Gate already exists; inspect it before replacing any settings")
    prototype = portals.get("crazy-world-progression:test")
    if prototype is None or plain(prototype).get("template") != "crazy-world-progression:__coordinates__":
        raise RuntimeError("Coordinate portal prototype is missing")
    for path, name, root in ((portal_path, portal_name, portal_root),
                             (pocket_path, pocket_name, pocket_root)):
        if gzip.decompress(path.read_bytes()) != gzip.decompress(encode(name, root)):
            raise RuntimeError(f"NBT does not round trip byte for byte: {path}")
    # The old save has no locations field. Materialize the same migrated
    # originals for existing portals, while deliberately leaving this gate unplaced.
    locations = portal_data.get("locations")
    if locations is None:
        locations = compound({})
        for key, definition in portals.items():
            fields = definition.value
            locations.value[key] = compound({"original": compound({
                "dimension": copy.deepcopy(fields["dimension"]),
                "x": copy.deepcopy(fields["center_x"]),
                "y": copy.deepcopy(fields["center_y"]),
                "z": copy.deepcopy(fields["center_z"]),
            })})
        portal_data["locations"] = locations
    gate = copy.deepcopy(prototype)
    fields = gate.value
    fields["id"] = string(gate_id)
    fields["dimension"] = string("minecraft:overworld")
    fields["center_x"] = number(0)
    fields["center_y"] = number(64)
    fields["center_z"] = number(0)
    fields["display_name"] = string("Gate")
    fields["entry_policy"] = string("once_per_attempt")
    fields["coordinate_target"] = compound({
        "x": number(-15), "y": number(64), "z": number(-10),
    })
    portals[gate_id] = gate
    locations.value[gate_id] = compound({})
    if "mob_scripts" not in pocket_data:
        pocket_data["mob_scripts"] = compound({})
    scripts = pocket_data["mob_scripts"].value
    pocket_id = "crazy-world-progression:raidtestpocket"
    attached = scripts.get(pocket_id, Tag(9, (8, [])))
    if attached.kind != 9 or attached.value[0] != 8:
        raise RuntimeError("Unexpected script attachment list")
    if "raidscript" not in attached.value[1]:
        attached.value[1].append("raidscript")
    scripts[pocket_id] = attached
    save_pair([(portal_path, portal_name, portal_root), (pocket_path, pocket_name, pocket_root)])
    print("Created unplaced cwp:gate and attached raidscript to raidtestpocket")


if __name__ == "__main__":
    main()
