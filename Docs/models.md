# LLM Models Capability Analysis

## Overview
This document summarizes the capabilities of the available Large Language Models (LLMs)
in the system, based on the provided model list.

- **Total Models:** 123
- Focus areas:
  - Reasoning / Thinking
  - Tool & Agent usage
  - Vision / Multimodal processing

---

## 📊 Global Statistics

| Capability | Count | Percentage |
|-----------|-------|------------|
| Total Models | 123 | 100% |
| Thinking / Reasoning Models | ~31 | ~25% |
| Tool / Agent-capable Models | ~55 | ~45% |
| Vision / Multimodal Models | ~18 | ~15% |

---

## 🧠 Thinking / Reasoning Models

Models designed for deep reasoning, step-by-step thinking, formal proofs,
or complex decision-making.

**Examples:**
- Qwen3-235B-A22B-Thinking-2507
- Qwen3-Next-80B-A3B-Thinking
- DeepSeek-R1 / DeepSeek-R1-0528
- DeepSeek-Prover-V2-671B
- Cogito-671B (and FP8 variants)
- Command-a-reasoning-08-2025
- Kimi-K2-Thinking

**Use cases:**
- Complex analysis
- Mathematical and logical reasoning
- Long-form decision support
- Formal proofs and verification

---

## 🛠️ Tool / Agent-Capable Models

Models suitable for:
- Function calling
- Tool orchestration
- API planning
- Code + command execution workflows

**Examples:**
- openai/gpt-oss-120b
- openai/gpt-oss-20b
- GLM-4.6 / GLM-4.5 family
- Qwen3 / Qwen3-Coder family
- DeepSeek-V3 / V3.1 / V3.2
- Hermes-4 (70B / 405B)
- Nemotron Ultra / Nano
- Command-r / Command-r7b

**Use cases:**
- Agentic workflows
- MCP servers
- Automation pipelines
- Software architecture & orchestration

---

## 🖼️ Vision / Multimodal Models

Models capable of understanding and processing images in addition to text.

**Examples:**
- Qwen3-VL (8B / 30B / 235B)
- Qwen2.5-VL (7B / 32B / 72B)
- GLM-4.6V / GLM-4.5V
- ERNIE-4.5-VL (28B / 424B)
- Cohere aya-vision-32b
- command-a-vision-07-2025

**Use cases:**
- Image understanding
- OCR
- UI / screenshot analysis
- Multimodal assistants

---

## 🧩 Engineering Notes

- The system is **reasoning-heavy**, not chat-only.
- Tool usage support is strong and suitable for large-scale automation.
- Vision support exists but is secondary to text and reasoning.
- Due to the large number of models, a **smart router policy** is essential
  to control cost, latency, and reliability.

---

## Recommendation

Introduce a capability-based router that selects models based on:
- Task type
- Required reasoning depth
- Tool usage
- Vision requirements
- Cost and latency constraints
