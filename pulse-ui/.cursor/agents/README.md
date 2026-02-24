# Pulse Sub-Agents

Specialized AI assistants for building Pulse features. Each solves specific problems in the development process.

## The Team

| Agent | Solves | Use When |
|-------|--------|----------|
| **product-manager** | Feature prioritization, requirements | "Should we build this?" |
| **ux-reviewer** | UX issues, accessibility | "Is this flow confusing?" |
| **ui-polish** | Visual inconsistencies | "Why does spacing look off?" |
| **react-architect** | Code architecture, types | "How should I structure this?" |
| **manual-tester** | Testing, bugs | "Test this implementation" |

---

## How They Work

### Automatic Usage
Agents proactively help when they detect their problems:
- `ux-reviewer` runs when navigation changes
- `ui-polish` checks visual consistency
- `manual-tester` validates after implementation

### Explicit Invocation
```
/product-manager Should we add Core Web Vitals?
/ux-reviewer Review the sticky navigation flow
/ui-polish Check spacing consistency
/react-architect How should I filter journey paths?
/manual-tester Test the journey filtering feature
```

---

## Integration with Cursor Ecosystem

### With Rules (`.cursor/rules/`)
Rules define **how** to write code. Agents decide **what** to build.

Example:
- **Rule**: "Use TypeScript strict mode"
- **Agent**: `react-architect` enforces type safety

### With Skills (`.cursor/skills/`)
Skills are **single-purpose actions**. Agents are **multi-step workflows**.

Example:
- **Skill**: "Format imports" (1 action)
- **Agent**: "Test this feature" (20+ checks)

### With Commands (`.cursor/commands/`)
Commands are **shortcuts**. Agents provide **expertise**.

Example:
- **Command**: `/test` runs tests
- **Agent**: `/manual-tester` tests + reports bugs + suggests fixes

---

## Problem-Solving Examples

### Problem: "Navbar looks weird when scrolling"

**Flow:**
1. **ux-reviewer** identifies UX issue (gap above nav)
2. **ui-polish** suggests gradient overlay solution
3. **react-architect** implements CSS
4. **manual-tester** verifies across browsers

**Result:** Glass morphism gradient fills gap smoothly

---

### Problem: "Journey paths show too many bounces"

**Flow:**
1. **product-manager** analyzes problem (bounces dominate)
2. **react-architect** implements filtering logic
3. **manual-tester** tests edge cases (empty, loading, errors)

**Result:** Only meaningful multi-screen paths shown

---

## When to Use Each Agent

### product-manager
**Problems solved:**
- ❓ "Is this feature worth building?"
- ❓ "What's the ROI?"
- ❓ "How do competitors solve this?"

**Real example:**
```
Input: "Should we show Core Web Vitals?"
Output: ❌ No - web-only metric, violates universal KPI principle
Alternative: Add device-agnostic "Slow Sessions" metric
```

---

### ux-reviewer
**Problems solved:**
- 🤔 "Can users complete this flow?"
- ♿ "Is this accessible?"
- 🚨 "Are error states clear?"

**Real example:**
```
Input: Sticky navigation added
Output: ⚠️ Gap above nav breaks visual continuity
Fix: Add gradient overlay extending upward
```

---

### ui-polish
**Problems solved:**
- 📐 "Why is spacing inconsistent?"
- 🎨 "Why do colors look off?"
- 💅 "Is this following design system?"

**Real example:**
```
Input: Navigation bar too tall
Output: Reduce padding from 12px → 8px
Use: var(--mantine-spacing-md) instead of hardcoded value
Result: 27% height reduction
```

---

### react-architect
**Problems solved:**
- 🏗️ "How should I structure this?"
- ⚡ "Why is this slow?"
- 🔍 "TypeScript error - how to fix?"

**Real example:**
```
Input: "Infinite journey paths - which to show?"
Output: Add pathLength, isBounce fields to type
Filter: j => j.pathLength >= 2 && j.sessionCount >= 5
Result: Only meaningful paths shown
```

---

### manual-tester
**Problems solved:**
- 🐛 "Does this work correctly?"
- 🌐 "Does this work in Safari?"
- ♿ "Can I use keyboard only?"

**Real example:**
```
Input: "Test journey filtering"
Output:
✅ Bounces filtered correctly
✅ Top 10 shown
✅ Works in Chrome, Safari, Firefox
⚠️ Path overflow on mobile (P3)
⚠️ Missing ARIA labels (P3)
Result: Pass with minor issues
```

---

## Best Practices

### ✅ Do
- Use agents for **specific problems** they solve
- Let agents run **proactively** (ux-reviewer, ui-polish)
- Chain agents for **complex workflows** (PM → Architect → Tester)
- Trust agent **expertise** (they know Pulse context)

### ❌ Don't
- Use agents for **simple tasks** (use skills instead)
- Invoke **wrong agent** for problem (check "Solves" column)
- Skip **testing phase** (manual-tester catches issues early)
- Ignore **agent recommendations** (they're based on best practices)

---

## Workflow Example

### Building a New Feature

1. **product-manager** → Validate it's worth building
2. **ux-reviewer** → Design the user flow
3. **ui-polish** → Define visual specs
4. **react-architect** → Implement the code
5. **manual-tester** → Test thoroughly

---

## Performance & Cost

Following [Cursor best practices](https://cursor.com/docs/context/subagents):

- **Fast model** for quick checks (ux-reviewer, ui-polish, manual-tester)
- **Inherit model** for complex analysis (product-manager, react-architect)
- **Context isolation** keeps main conversation clean
- **Proactive usage** catches issues early (cheaper than fixing later)

---

## Files

```
pulse-ui/
  .cursor/
    agents/
      product-manager.md      # Feature prioritization
      ux-reviewer.md          # UX + accessibility
      ui-polish.md            # Visual consistency
      react-architect.md      # Code architecture
      manual-tester.md        # QA testing
      README.md               # This file
```

---

## Quick Start

Ask natural questions and the right agent will help:

```
"Should we add this feature?"              → product-manager
"This navigation feels confusing"          → ux-reviewer  
"Why is the spacing inconsistent?"        → ui-polish
"How do I type this complex state?"       → react-architect
"Test the new filtering implementation"   → manual-tester
```

---

## Measuring Success

**Good Collaboration:**
- ✅ Features ship with minimal bugs
- ✅ Design and code match specs
- ✅ Issues caught before production
- ✅ Fast iteration cycles

**Poor Collaboration:**
- ❌ Frequent bugs found in production
- ❌ Design doesn't match implementation
- ❌ Rework required after testing
- ❌ Features built that users don't need

---

## Need Help?

- **Feature prioritization**: `/product-manager`
- **UX problems**: `/ux-reviewer`
- **Visual issues**: `/ui-polish`
- **Code architecture**: `/react-architect`
- **Testing**: `/manual-tester`

Let the agents solve your problems! 🚀
