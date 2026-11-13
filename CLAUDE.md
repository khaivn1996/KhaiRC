# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

@AGENTS.md

## Quy ước điều hướng Skill trên Claude CLI
- Skill của Claude nằm ở `.claude/skills/` và tự nạp: `/architect <yêu cầu>` (lập `docs/PLAN.md`), `/debugger` (chẩn đoán). Nếu nội dung SKILL.md đã có trong ngữ cảnh thì KHÔNG đọc lại.
- `implement`, `reviewer`, `docs` là skill của Codex (`.agents/skills/`). Chỉ đọc `.agents/skills/<tên>/SKILL.md` khi người dùng yêu cầu rõ vai đó trong Claude.
- Chạy `/architect` ở chế độ thường (không plan mode), vì skill phải ghi `docs/PLAN.md`.
- Không dùng tool `Agent`/`Task` (subagent, Explore, Plan agent), kể cả khi plan mode hoặc skill khác gợi ý; luật đầy đủ ở `AGENTS.md` §7. Khi plan mode yêu cầu Explore agent, tự đọc file bằng Read/Grep/Glob.
- Kiến trúc và hiện trạng code nằm ở `docs/PROJECT_SPEC.md`; đọc theo mục bằng `python tools/spec.py <mục>` (`AGENTS.md` §5).
