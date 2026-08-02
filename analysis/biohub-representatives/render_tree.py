#!/usr/bin/env python3
"""Render an IQ-TREE Newick tree as a publication-oriented vector PDF."""

from __future__ import annotations

import argparse
from dataclasses import dataclass, field
from pathlib import Path

from reportlab.lib import colors
from reportlab.lib.pagesizes import A4, landscape
from reportlab.pdfgen import canvas


@dataclass
class Node:
    name: str = ""
    length: float = 0.0
    children: list["Node"] = field(default_factory=list)
    x: float = 0.0
    y: float = 0.0


def parse_newick(text: str) -> Node:
    index = 0

    def token(stoppers: str) -> str:
        nonlocal index
        start = index
        while index < len(text) and text[index] not in stoppers:
            index += 1
        return text[start:index].strip()

    def node() -> Node:
        nonlocal index
        children = []
        if text[index] == "(":
            index += 1
            while True:
                children.append(node())
                if text[index] == ",":
                    index += 1
                    continue
                if text[index] != ")":
                    raise ValueError(f"expected ')' at {index}")
                index += 1
                break
        name = token(":,();")
        length = 0.0
        if index < len(text) and text[index] == ":":
            index += 1
            value = token(",();")
            length = float(value)
        return Node(name=name, length=length, children=children)

    root = node()
    if index < len(text) and text[index] == ";":
        index += 1
    if text[index:].strip():
        raise ValueError("trailing Newick content")
    return root


def leaves(node: Node):
    if not node.children:
        yield node
    for child in node.children:
        yield from leaves(child)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("newick", type=Path)
    parser.add_argument("pdf", type=Path)
    args = parser.parse_args()
    root = parse_newick(args.newick.read_text().strip())

    def assign_x(node: Node, parent_x: float = 0.0):
        node.x = parent_x + node.length
        for child in node.children:
            assign_x(child, node.x)

    assign_x(root)
    leaf_nodes = list(leaves(root))
    for i, leaf in enumerate(leaf_nodes):
        leaf.y = i

    def assign_y(node: Node):
        for child in node.children:
            assign_y(child)
        if node.children:
            node.y = sum(child.y for child in node.children) / len(node.children)

    assign_y(root)
    width, height = landscape(A4)
    left, right, bottom, top = 58, 205, 58, 72
    max_x = max(n.x for n in leaf_nodes)
    xscale = (width - left - right) / max_x
    yscale = (height - bottom - top) / max(1, len(leaf_nodes) - 1)
    c = canvas.Canvas(str(args.pdf), pagesize=(width, height))
    c.setTitle("BioHub representative maximum-likelihood phylogeny")
    c.setFont("Helvetica-Bold", 14)
    c.drawString(left, height - 35, "BioHub representative-panel maximum-likelihood phylogeny")
    c.setFont("Helvetica", 8)
    c.drawString(left, height - 50, "MAFFT L-INS-i; IQ-TREE 3.1.2; LG+G4; node labels: SH-aLRT / ultrafast bootstrap (%)")

    def px(node: Node) -> float:
        return left + node.x * xscale

    def py(node: Node) -> float:
        return bottom + node.y * yscale

    def draw(node: Node):
        if node.children:
            c.setStrokeColor(colors.HexColor("#475569"))
            c.setLineWidth(0.7)
            c.line(px(node), min(py(ch) for ch in node.children), px(node), max(py(ch) for ch in node.children))
            for child in node.children:
                c.line(px(node), py(child), px(child), py(child))
                if child.name and child.children:
                    c.setFont("Helvetica", 6.5)
                    c.setFillColor(colors.HexColor("#334155"))
                    c.drawString(px(node) + 2, py(child) + 2, child.name)
                draw(child)
        else:
            color = colors.HexColor("#b91c1c") if node.name.startswith("HUMAN_") else colors.HexColor("#0f4c81")
            c.setFillColor(color)
            c.setFont("Helvetica-Bold" if node.name.startswith("HUMAN_") else "Helvetica", 8)
            c.drawString(px(node) + 4, py(node) - 2, node.name)

    draw(root)
    bar_length = 0.5
    bar_x, bar_y = left, 28
    c.setStrokeColor(colors.black)
    c.setLineWidth(1.0)
    c.line(bar_x, bar_y, bar_x + bar_length * xscale, bar_y)
    c.line(bar_x, bar_y - 2, bar_x, bar_y + 2)
    c.line(bar_x + bar_length * xscale, bar_y - 2, bar_x + bar_length * xscale, bar_y + 2)
    c.setFont("Helvetica", 7)
    c.setFillColor(colors.black)
    c.drawCentredString(bar_x + bar_length * xscale / 2, bar_y - 11, "0.5 substitutions/site")
    c.drawRightString(width - 25, 18, "Red: human references; blue: BioHub representatives")
    c.showPage()
    c.save()


if __name__ == "__main__":
    main()
