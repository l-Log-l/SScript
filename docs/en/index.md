---
title: EN Home
nav_order: 1
parent: Docs Home
has_children: true
---

# SScript (English)

**SScript** is a programming language for Minecraft server automation. Write scripts in Python-like syntax to execute commands, automate tasks, and control server behavior.

## Core Purpose

- 🎯 **Command Execution** — Run commands programmatically (`/say`, `/give`, `/setblock`, etc.)
- ⚙️ **Task Automation** — Startup scripts, scheduled tasks, bulk operations
- 🔧 **Server Logic** — React to player actions (join, chat, block breaks, deaths)
- 📝 **Simple Syntax** — Easy to learn and use
- ⚡ **Non-blocking** — Tasks run without freezing the server

## Documentation Map

### Basics
- [Getting Started](getting-started.md) — Installation and first script
- [Language Guide](language.md) — Variables, types, control flow
- [Engine Overview](engine-overview.md) — Quick overview of everything

### Functions & Events
- [Functions & Async Mechanics](functions-mechanics.md) — func/def, wait, async patterns, fire-and-forget
- [Complete Events Reference](complete-events.md) — All 13 events with examples
- [Complete Built-in Reference](complete-reference.md) — All 60+ functions
- [Data Structures & Objects](data-structures.md) — Block, Player, Response, Command objects

### Commands & Control
- [Commands Reference](commands.md) — `/sscript run`, `monitor`, `stop`, `reload`, `debug`

### Advanced Topics
- [Advanced Features & Mechanics](advanced-features.md) — Try-catch, selectors, NBT, HTTP headers, limits
- [Architecture & API](architecture.md) — Internal design, ProcessScheduler, mixin points

### Examples
- [Examples](examples.md) — Ready-to-use projects and recipes
