# Home Visual Atmosphere V4

**Status:** Approved visual direction from the user conversation  
**Screen:** React Home / Inicio  
**FRAME CHANGE:** YES, limited to shared typography. Home atmospheric motion remains screen-local.

## Goal

Make RecepVoz feel professionally art-directed while preserving its current dark petroleum, cyan, violet, neon, robot and glow identity.

## Approved visual decisions

1. **Keep the current light language.**
   - Preserve cyan, violet and emerald glows.
   - Preserve the robot artwork and illuminated controls.
   - Do not flatten the interface or remove neon depth.

2. **Replace the accidental browser serif typography.**
   - The shared UI must use a professional sans-serif stack.
   - Use the existing-product-compatible stack:
     `Inter, "Segoe UI Variable", "Segoe UI", ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, sans-serif`.
   - Apply it from the shared foundation so React screens do not fall back to browser serif defaults.
   - Keep numeric metrics stable with tabular numerals where appropriate.

3. **Make the petroleum background visibly alive without moving the information.**
   Home must contain four low-intensity atmospheric layers:
   - flowing luminous wave field;
   - drifting digital/grid texture;
   - sparse particles/data points;
   - slow breathing halo/light field.

4. **Motion character.**
   - Ambient loops: 8–20 seconds.
   - Text, numbers and labels stay readable and stationary.
   - Motion belongs to atmosphere, reflections and background energy.
   - No bounce-heavy motion, aggressive flashes or constant attention stealing.
   - Existing robot and status micro-motion remain.

5. **Reduced motion.**
   - All new infinite ambient motion must stop under `prefers-reduced-motion: reduce`.

6. **Frame safety.**
   - Do not change protected sidebar width, topbar geometry, page gutters, content max width or canonical breakpoints.
   - Shared typography is the only intentional protected visual-system change.

## Visual acceptance

At desktop, tablet and mobile canonical widths, Home must:
- retain current readable hierarchy and compact hero height;
- show no horizontal overflow;
- preserve all operational content and interactions;
- visibly carry cyan/violet energy through petroleum areas instead of leaving them flat;
- keep the atmosphere behind content;
- use sans typography consistently instead of serif browser defaults.
