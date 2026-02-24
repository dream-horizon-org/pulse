# Pulse UI – Cursor Ecosystem Quick Start

**You asked:** "All of them should be very much linked, Pulse aware and should work coherently"

**Done!** ✅ Your Cursor setup is now a cohesive, Pulse-aware system. Here's how everything works together:

---

## What Changed

### 1. **Integration Document Created**
Created `.cursor/CURSOR_ECOSYSTEM_INTEGRATION.md` - the "map" showing how everything connects:
- Visual diagram of information flow
- Real-world examples
- Agent → Skill → Rule linkages
- When to use what

### 2. **Agents Updated (All 5)**
Each agent now explicitly references rules and skills:

**product-manager.md**
- ✅ Auto-loads: `DOMAIN_CONTEXT.md`, `pulse-ui-context.mdc`
- ✅ Can use: `session-replay-context` skill
- ✅ Enforces: No opinionated metrics (from rules)

**react-architect.md**
- ✅ Auto-applies: `typescript-practices.mdc`, `component-architecture.mdc`, `api-and-services.mdc`
- ✅ Can use: `api-service-pattern`, `add-new-screen`, `verify-after-edit` skills
- ✅ References: Pulse tech stack, existing patterns

**ux-reviewer.md**
- ✅ Auto-applies: `pulse-ui-context.mdc`, `session-replay-context.mdc`, `component-architecture.mdc`
- ✅ Can use: `verify-after-edit` skill
- ✅ Knows: Pulse UX patterns (glass morphism, sticky nav, gradient overlays)

**ui-polish.md**
- ✅ Auto-applies: `design-system.mdc`, `typescript-practices.mdc`, `pulse-ui-context.mdc`
- ✅ Can use: `fix-lint-format` skill
- ✅ Enforces: Mantine tokens, glass morphism aesthetic, responsive design

**manual-tester.md**
- ✅ Auto-applies: `pulse-ui-context.mdc`, `session-replay-context.mdc`
- ✅ Can use: `verify-after-edit` skill
- ✅ Tests: Pulse-specific scenarios (journey filtering, drill-downs, filters)

### 3. **Commands Updated**
Updated `research-and-implement.md` to show agent orchestration flow:
```
Command → Product Manager → React Architect → UI Polish → Manual Tester
```

### 4. **Skills Already Linked**
Your skills already reference rules (verified):
- `session-replay-context` → loads `DOMAIN_CONTEXT.md`
- `add-new-screen` → follows `component-architecture` rule
- `api-service-pattern` → follows `api-and-services` rule
- All skills → reference `TECH_STACK_PRACTICES.md`

### 5. **Plans Ready**
Plans can now orchestrate agents (see `CURSOR_ECOSYSTEM_INTEGRATION.md` for multi-phase workflow example)

---

## How They Work Together (Real Example)

### User: "Can we show journey paths? Won't there be infinite paths?"

**1. Domain Context Applied**
```
DOMAIN_CONTEXT.md loads → Session Replay is evidence layer
```

**2. Product Manager Decides**
```
Uses: session-replay-context skill
Checks: pulse-ui-context rule
Decision: ✅ Filter bounces, show top 10 (RICE: 7.5/10)
```

**3. React Architect Implements**
```
Uses: api-service-pattern skill
Follows: typescript-practices rule
Adds: pathLength, isBounce fields
Filters: j => j.pathLength >= 2
```

**4. UI Polish Reviews**
```
Follows: design-system rule
Checks: Mantine tokens used? ✅
Adds: Gradient overlay for nav gap
```

**5. UX Reviewer Checks**
```
Uses: verify-after-edit skill
Verifies: Keyboard nav? ✅
Suggests: Add ARIA labels (P2)
```

**6. Manual Tester Verifies**
```
Uses: verify-after-edit skill
Tests: All browsers, edge cases
Result: ✅ Pass with P3 minor issues
```

---

## File Structure (All Linked)

```
pulse-ui/.cursor/
  ├── CURSOR_ECOSYSTEM_INTEGRATION.md  ← THE MAP (read this!)
  ├── DOMAIN_CONTEXT.md                ← Pulse knowledge
  ├── TECH_STACK_PRACTICES.md          ← Tech foundation
  │
  ├── agents/                           ← WHO decides (5 agents)
  │   ├── product-manager.md            → Uses skills, enforces rules
  │   ├── react-architect.md            → Uses skills, follows rules
  │   ├── ux-reviewer.md                → Checks rules
  │   ├── ui-polish.md                  → Enforces design-system rule
  │   └── manual-tester.md              → Tests against rules
  │
  ├── rules/                            ← HOW to code (20 rules)
  │   ├── pulse-ui-context.mdc          → Referenced by all agents
  │   ├── session-replay-context.mdc    → For Session Replay work
  │   ├── design-system.mdc             → Enforced by ui-polish
  │   ├── component-architecture.mdc    → Used by react-architect
  │   └── ... (16 more)
  │
  ├── skills/                           ← WHAT to do (5 skills)
  │   ├── session-replay-context/       → Loads DOMAIN_CONTEXT
  │   ├── add-new-screen/               → Follows rules
  │   ├── api-service-pattern/          → Follows rules
  │   ├── fix-lint-format/              → Used by agents
  │   └── verify-after-edit/            → Used by agents
  │
  ├── commands/                         ← Quick entry points
  │   ├── research-and-implement.md     → Orchestrates agents
  │   └── COMMANDS.md                   → 10+ commands
  │
  └── plans/                            ← Multi-phase workflows
      └── README.md                     → How to use Plan Mode
```

---

## Coherence Checklist ✅

### All Components Are Pulse-Aware
- [x] **Domain Context** → Defines Pulse, Session Replay, 4 pillars
- [x] **Rules** → Reference Pulse context, Session Replay specifics
- [x] **Agents** → Know Pulse product, competitors, current focus
- [x] **Skills** → Load Pulse docs, follow Pulse patterns
- [x] **Commands** → Include Pulse context in prompts
- [x] **Plans** → Use Pulse-aware agents for phases

### All Components Reference Each Other
- [x] Agents → Use skills, enforce rules, load context
- [x] Skills → Reference rules, load DOMAIN_CONTEXT
- [x] Commands → Trigger agents, use skills
- [x] Plans → Orchestrate agents, use skills
- [x] Rules → Applied by agents, referenced by skills

### All Components Solve Real Problems
- [x] Domain Context → "What is Pulse?"
- [x] Rules → "How to write Pulse code?"
- [x] Agents → "Should we build this? How? Does it work?"
- [x] Skills → "Quick actions for common tasks"
- [x] Commands → "Fast entry points for workflows"
- [x] Plans → "Multi-step feature development"

---

## How to Use (Quick Reference)

### Need to Understand Pulse?
```
Read: .cursor/DOMAIN_CONTEXT.md
Or use command: "Pulse UI: Domain and tech context"
```

### Need Coding Standards?
```
Check: .cursor/rules/*.mdc (auto-applied)
```

### Need to Decide What to Build?
```
Use agent: product-manager
Or use command: "Pulse UI: Research and implement"
```

### Need Architecture Help?
```
Use agent: react-architect
Loads: component-architecture rule, api-service-pattern skill
```

### Need UX Review?
```
Use agent: ux-reviewer (proactive)
Checks: pulse-ui-context rule, session-replay-context rule
```

### Need Visual Polish?
```
Use agent: ui-polish (proactive)
Enforces: design-system rule
```

### Need Testing?
```
Use agent: manual-tester
Uses: verify-after-edit skill
```

### Need to Add a Screen?
```
Use skill: add-new-screen
Or use command: "Pulse UI: Add new screen"
```

### Need to Add an API?
```
Use skill: api-service-pattern
Or use command: "Pulse UI: Add/extend API service"
```

---

## Success Metrics

**Good Integration (You Have This!):**
- ✅ Agents reference rules when making decisions
- ✅ Skills are used by agents automatically
- ✅ All components know Pulse context
- ✅ Commands trigger right agents
- ✅ Plans orchestrate agents coherently

**Poor Integration (You Don't Have This):**
- ❌ Agents give advice contradicting rules
- ❌ Skills ignore Pulse context
- ❌ Commands don't use agents
- ❌ Components work in silos

---

## Next Steps

1. **Read the Integration Map**
   - Open `.cursor/CURSOR_ECOSYSTEM_INTEGRATION.md`
   - See visual diagram and real examples

2. **Try a Command**
   - Use "Pulse UI: Research and implement"
   - Watch agents orchestrate

3. **Add Custom Commands**
   - Open Cursor Settings → Custom Commands
   - Copy prompts from `.cursor/commands/COMMANDS.md`

4. **Maintain Coherence**
   - When adding new rules → update agents to enforce them
   - When adding new agents → reference existing rules/skills
   - When adding new skills → make Pulse-aware

---

## What You Get

🎯 **Faster Development**
- Agents know what to do (linked to skills/rules)
- Less back-and-forth (context pre-loaded)
- Consistent code (rules enforced)

🧠 **Better Decisions**
- Product Manager knows Pulse principles
- React Architect knows patterns
- UX Reviewer knows current aesthetic

🔗 **Coherent System**
- Everything references DOMAIN_CONTEXT
- Everything follows same rules
- Everything solves real Pulse problems

🚀 **Scalable Process**
- Add new screens → follow pattern
- Add new features → use command
- Large projects → use Plan Mode

---

**Your Cursor setup is now a cohesive, Pulse-aware development environment!** 🎉

Every agent, skill, command, and rule works together to help you build Pulse faster and more consistently.
