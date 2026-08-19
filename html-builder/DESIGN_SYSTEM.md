# CoderBuilder 2.0 — Visual Standard

This document is the default UI/UX reference for all CoderBuilder versions (Android and Windows).

## Identity

- Product name: **CoderBuilder**
- Tagline: **Cole. Crie. Execute.**
- Style: premium developer tool, dark navy, blue/cyan neon accents, rounded cards, subtle borders and glow.
- Interface language: Portuguese (pt-BR).

## Core colors

- App background: `#07111F`
- Elevated background: `#0B1728`
- Card: `#0E1D31`
- Card highlight: `#132844`
- Border: `#23466F`
- Primary blue: `#1479FF`
- Bright blue: `#23A9FF`
- Cyan: `#19D5D2`
- Text primary: `#F4F8FF`
- Text secondary: `#A8B7CE`
- Success: `#37D58A`
- Warning: `#FFB454`

## Shape and spacing

- Main panels: 14–18 px radius.
- Buttons and inputs: 10–14 px radius.
- Thin blue-gray borders with low contrast.
- Spacing rhythm: 8 / 12 / 16 / 24 / 32 px.
- Avoid visual clutter; editor and preview are always the main focus.

## Desktop layout

1. Top bar with logo/name and primary tabs: Editor, Visualizar, Configurações.
2. Left sidebar: file name/path, save/open/new actions and contextual tips.
3. Main area: code editor on top, live preview below.
4. Status bar: version, readiness state, line/column information and theme.
5. Large screens keep a two-column structure; narrow screens collapse to one column.

## Android layout

1. Compact brand header.
2. File card.
3. Editor card using monospace typography.
4. Primary actions: Visualizar, Salvar HTML, Abrir no navegador.
5. Live preview card below editor.
6. Bottom navigation may be added later for Editor, Arquivos, Snippets and Configurações.

## Interaction rules

- Primary CTA uses blue gradient/bright blue treatment.
- Destructive or clearing actions are visually secondary and require intent.
- Preview should update without forcing the user to save first.
- Saving always normalizes `.html` / `.htm` extensions.
- Keep the previous stable version untouched while V2 remains alpha.

## Versioning

The first implementation of this standard is **CoderBuilder 2.0.0-alpha.1** on branch `coderbuilder-v2-alpha`.
