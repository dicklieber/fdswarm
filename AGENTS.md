# AGENTS.md — fdswarm

## Project Overview

fdswarm is a distributed amateur radio contest logging application.

It is implemented in Scala 3 using ScalaFX/JavaFX and Mill.

The application is used primarily during Winter Field Day and
ARRL Field Day events.

Multiple independent nodes operate on a local LAN, exchanging QSO
records using UDP broadcast.

The architecture deliberately favors simplicity, reliability,
and ease of reasoning over sophisticated distributed protocols.

This is a working application. Preserve existing behavior unless
a change is explicitly requested.


## Development Environment

- Language: Scala 3
- Build tool: Mill 2.0
- JDK: BellSoft Liberica JDK 21 Full (includes JavaFX)
- UI: ScalaFX / JavaFX
- IDE: IntelliJ IDEA Ultimate
- Testing: MUnit
- Code coverage: scoverage

Use the Scala and library versions defined by the project build.

Do not assume that newer dependency versions should be adopted.


## Division of Responsibilities

### Codex Responsibilities

Codex is responsible for:

- Analyzing existing code.
- Reviewing code for correctness and maintainability.
- Identifying potential bugs.
- Implementing requested features.
- Fixing bugs.
- Refactoring and simplifying code when requested.
- Writing and updating tests.
- Investigating architectural issues.
- Suggesting improvements when appropriate.
- Explaining significant design decisions.

### Developer Responsibilities

The developer is responsible for:

- Building the project using Mill.
- Running tests.
- Debugging interactively.
- Reviewing changes in IntelliJ.
- Making manual code changes.
- Managing Git branches and commits.
- Packaging and releasing applications.
- Deciding whether proposed architectural changes are appropriate.

Codex and IntelliJ operate on the same working directory.

Do not create separate Git worktrees unless explicitly requested.


## Task Execution

Interpret requests according to their intent.

### Analysis and Review

When asked to analyze, investigate, evaluate, or review:

- Inspect the relevant code.
- Identify problems and potential improvements.
- Explain findings clearly.
- Distinguish confirmed problems from suspected problems.
- Recommend solutions when appropriate.
- Do not modify files unless explicitly requested.

### Implementation

When asked to implement, fix, or refactor:

- Modify source files directly.
- Make focused changes.
- Avoid unrelated modifications.
- Do not reformat unrelated code.
- Preserve existing behavior unless changes are requested.
- Follow existing project conventions.
- Add or update appropriate tests.
- Do not remove tests merely to make a change appear successful.

### Architectural Changes

For substantial architectural changes:

- Analyze the existing implementation first.
- Explain the proposed approach.
- Identify important tradeoffs.
- Prefer the simplest solution that satisfies the requirements.
- Obtain approval before making substantial architectural changes.

Do not introduce unnecessary frameworks, infrastructure,
abstractions, or dependencies.


## Build Tool

This project uses Mill 2.0, not sbt.

- Never run sbt commands.
- Do not run Mill builds or tests unless explicitly requested.
- Do not modify build dependencies without approval.
- Do not modify build configuration unnecessarily.

When explicitly requested to run Mill:

- Prefer `--no-server`.
- Prefer an output directory outside the repository.
- Avoid interfering with IntelliJ's build state.

For example:

    MILL_OUTPUT_DIR=/private/tmp/fdswarm-mill-out \
      ./mill --no-server ...

Do not assume a successful source edit means the project compiles.

If builds or tests were not executed, explicitly say so.


## Git Operations

The developer manages Git.

Codex must not:

- Commit changes.
- Push changes.
- Create or delete branches.
- Create or delete tags.
- Rebase or merge branches.
- Reset or discard existing changes.
- Modify Git configuration.

These operations require explicit authorization.

Read-only Git commands are permitted when useful for analysis.

Do not overwrite or discard changes made by the developer.


## Scala Coding Style

Prefer straightforward, idiomatic Scala 3.

- Prefer Scala 3 idioms over Java-style programming.
- Prefer case classes and enums where appropriate.
- Prefer immutable values where practical.
- Use `for/yield` comprehensions when they improve readability.
- Prefer expressive collection operations over manual loops.
- Avoid unnecessary mutable state.
- Mutable state is acceptable for ScalaFX and application state.
- Avoid excessive abstraction and type-level programming.
- Avoid clever code that is difficult to understand.
- Favor code that clearly expresses the problem domain.
- Follow existing naming and formatting conventions.

Prefer code that can be understood without extensive
knowledge of implementation details.

Do not introduce additional abstractions merely to reduce
the number of lines of code.

A straightforward implementation is preferable to a
more sophisticated implementation that is harder to reason about.


## ScalaFX / JavaFX

- Preserve existing ScalaFX application architecture.
- Respect JavaFX application thread requirements.
- Keep UI updates on the JavaFX Application Thread.
- Avoid blocking the JavaFX Application Thread.
- Preserve observable collection behavior.
- Avoid unnecessary synchronization.
- Do not replace ScalaFX with another UI framework.
- Do not introduce additional UI frameworks without approval.

Mutable UI state is normal and acceptable.

Prefer clear separation between UI behavior and
application-domain logic.


## UI Style Rules

When generating or modifying UI:

- Never allow labels, values, headings, buttons, table cells,
  badges, navigation items, or other visible text to render
  as collapsed, clipped, overlapped, or unreadable.

- Prefer wrapping, responsive sizing, and layout changes
  over truncation.

- For HTML/CSS interfaces, use constraints such as:
  `min-width`, `max-width`, `flex-wrap`,
  `grid-template-columns`, `minmax()`, and `overflow-wrap`.

- Do not use `overflow: hidden`, `text-overflow: ellipsis`,
  `white-space: nowrap`, or tiny font sizes for important
  text unless truncation is explicitly requested or the
  full value is available elsewhere.

- For JavaFX interfaces, prefer appropriate layout panes,
  sizing constraints, and text wrapping.

- Preserve readability at realistic window sizes.

- Avoid fixed dimensions when content-dependent sizing
  is more appropriate.


## Distributed Architecture

fdswarm uses a deliberately simple distributed architecture.

### Network

- The application operates primarily on an isolated local LAN.
- Internet connectivity must never be required for normal operation.
- Nodes communicate using UDP broadcast.
- Every node is interested in every other node's data.
- Do not replace broadcast with multicast without a clear reason.
- Avoid introducing centralized coordination services.
- Nodes must continue operating independently when disconnected.

### QSO Data

- Each node maintains its own in-memory QSO dataset.
- QSO records have stable UUID identities.
- Synchronization is eventually consistent.
- Nodes may temporarily have different datasets.
- Nodes must be able to converge after reconnecting.

### Dataset Size

The contest dataset is intentionally small enough to fit in memory.

- Typical contests contain fewer than 10,000 QSOs.
- The practical maximum is approximately 40,000 QSOs.
- The entire dataset fits comfortably in memory.

Do not optimize for millions of records.

Do not introduce databases, persistent message queues,
or complex indexing structures without explicit approval.

Simple full-dataset operations may be entirely appropriate
at these data volumes.


## Synchronization Principles

Prefer simple, deterministic synchronization algorithms.

The existing architecture intentionally favors simplicity
over minimizing network traffic.

In particular:

- Sending an entire dataset can be preferable to complicated
  incremental synchronization.
- Receiving nodes can efficiently ignore records they already have.
- Avoid elaborate protocols that attempt to minimize small
  amounts of LAN traffic.
- Avoid unnecessary distributed coordination.
- Preserve eventual consistency.
- Preserve correct behavior when nodes disconnect and reconnect.

When evaluating synchronization changes, prioritize:

1. Correctness.
2. Simplicity.
3. Ease of reasoning.
4. Reliability.
5. Performance.

Performance optimizations must be justified by actual
requirements or measurements.

Do not replace a simple working algorithm with a more
complicated one merely because it appears more efficient.


## QSO Corrections and Deletions

QSO correction and deletion behavior requires particular care
because records are replicated between independent nodes.

When investigating changes:

- Preserve stable QSO identities.
- Consider delayed and duplicate messages.
- Consider nodes that disconnect and reconnect.
- Avoid allowing deleted records to reappear accidentally.
- Ensure that synchronization can eventually converge.
- Prefer deterministic conflict-resolution behavior.

Tombstones are a potential approach for representing deletions,
but they are not yet an established architectural requirement.

Do not implement tombstones or another deletion protocol
without explicit approval.


## Concurrency and Resource Management

When reviewing or modifying code:

- Identify potential race conditions.
- Respect JavaFX threading requirements.
- Avoid unnecessary blocking.
- Preserve thread safety of shared application state.
- Ensure sockets, streams, and other resources are closed properly.
- Avoid introducing unnecessary background threads.
- Prefer existing concurrency mechanisms and libraries.
- Do not introduce new concurrency frameworks without approval.

Do not add synchronization merely as a precaution.

Understand the actual concurrency requirements first.


## Testing

Use the project's existing MUnit testing conventions.

When implementing changes:

- Add tests for new behavior where appropriate.
- Update tests affected by intentional behavior changes.
- Preserve existing regression tests.
- Prefer deterministic tests.
- Avoid unnecessary timing dependencies.
- Do not introduce external service dependencies into tests.

For distributed behavior, consider:

- Duplicate messages.
- Out-of-order messages.
- Missing messages.
- Disconnected nodes.
- Reconnected nodes.
- Repeated synchronization.
- Conflicting updates.

Codex should write tests but leave their execution to
the developer unless explicitly requested.

Do not claim tests passed unless they were actually executed.


## Dependencies

- Prefer existing project libraries.
- Do not add dependencies without approval.
- Do not upgrade dependencies without approval.
- Avoid introducing libraries for trivial functionality.
- Do not replace existing frameworks without approval.

When proposing a dependency:

- Explain why it is needed.
- Explain what problem it solves.
- Consider whether existing libraries already provide
  the required functionality.


## Code Review Priorities

When reviewing code, prioritize:

1. Correctness.
2. Potential bugs.
3. Concurrency and synchronization problems.
4. Resource management.
5. Error handling.
6. Unnecessary complexity.
7. Maintainability.
8. Performance issues supported by evidence.
9. Style and formatting.

Do not prioritize cosmetic changes over correctness.

Distinguish between:

- Confirmed bugs.
- Likely bugs.
- Potential risks.
- Optional improvements.

Avoid speculative warnings unsupported by the code.


## Completion Reports

After implementing changes, provide a concise summary.

Include:

- What changed.
- Which files were modified.
- Important design decisions.
- Tests added or modified.
- Tests the developer should run.
- Any known limitations or unresolved issues.

Clearly state when builds or tests were not run.

Do not produce lengthy explanations for trivial changes.

For substantial changes, explain enough that the developer
can understand and evaluate the implementation.

## Interaction Policy

- Work autonomously on assigned tasks.
- Avoid unnecessary questions and confirmation requests.
- Do not ask for permission to make ordinary source code changes.
- Make reasonable implementation decisions independently.
- Do not stop after each step to ask whether to continue.
- Complete the entire requested task before reporting results.
- Ask questions only when essential information is missing
  or a decision would substantially change the requested behavior.
- If an operation is blocked by the sandbox, use an alternative
  that stays within the sandbox when possible.
- If blocked without a reasonable alternative, explain the
  limitation in the final report.
- Do not repeatedly request authorization for the same operation.

## General Principles

- Preserve working behavior.
- Prefer simple solutions.
- Avoid unnecessary complexity.
- Make focused changes.
- Respect existing architecture.
- Favor clarity over cleverness.
- Keep the developer in control of builds and Git.
- Ask before making substantial architectural changes.

The goal is reliable, understandable, maintainable code,
not maximum abstraction or minimum line count.