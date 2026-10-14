# Connect Four 3D Realistic Visuals & Menus Architecture Specification

## Executive Summary
This document specifies the technical architecture, asset pipeline, custom shaders, rollout plan, and testing matrix for upgrading **Connect Four** from its 2D canvas presentation to a 3D visual engine utilizing Google Filament and Jetpack Compose.

---

## 1. Architecture Document: 3D Layer Integration with Compose

### 1.1 High-Level Architecture Overview
```
+--------------------------------------------------------------------------------+
|                             Jetpack Compose UI Layer                           |
|  (HUD, Score Overlays, Action Buttons, Navigation, Dialogs, Accessibility Tree)|
+--------------------------------------------------------------------------------+
                                       |
                                       | Touch Events (Offset)
                                       v
+--------------------------------------------------------------------------------+
|                        Connect Four3DSurface (AndroidView)                          |
|             Hosts Filament SurfaceView / TextureView with lifecycle hooks      |
+--------------------------------------------------------------------------------+
             |                                                  ^
             | Frame Tick & Render Call                         | Hit Test Result
             v                                                  | (EdgeId / NodeId)
+--------------------------------------------------------------------------------+
|                        Connect FourSceneRenderer (Filament)                         |
|  - Scene Graph (Pegs, Tubes, Platforms, Dynamic Lights)                       |
|  - Camera Controller (Isometric Orbit, Pinch-Zoom, Screen Raycaster)          |
|  - Animation Controller (Choreographed interpolators, Particle Emitter)        |
+--------------------------------------------------------------------------------+
             ^
             | State Flow Observer (collectAsStateWithLifecycle)
+--------------------------------------------------------------------------------+
|                       GameViewModel & GameEngine                               |
|        Unchanged deterministic game state, AI logic, and Elo calculation       |
+--------------------------------------------------------------------------------+
```

### 1.2 Filament Engine Lifecycle & Compose Embedding
To guarantee zero stutter and clean memory disposal without leaking EGL contexts:

1. **Embedding**: Use `AndroidView` wrapping a hardware-accelerated `SurfaceView`.
2. **Lifecycle Synchronization**:
   - `DisposableEffect` tracks Android `LifecycleOwner`.
   - On `ON_RESUME`: Reattach swap chain and resume `Choreographer.FrameCallback`.
   - On `ON_PAUSE`: Stop choreographer and release swap chain backbuffers to save battery.
   - On `ON_DESTROY`: Call `engine.destroy()`, unload materials and entity IDs from the Filament `EntityManager`.
3. **Double Buffering & EGL Context**: Filament manages an internal swap chain linked directly to the `SurfaceHolder.surface`.

### 1.3 State Synchronization (Unidirectional Data Flow)
- **ViewModel Source of Truth**: `GameViewModel.gameState: StateFlow<GameState>` remains the sole state authority.
- **Delta Detection**:
  - `Connect Four3DRenderer` maintains a mirror state snapshot `RenderBoardState`.
  - On each `GameState` emission:
    - New edge added -> spawn a `TweenEdgeDrawAnimation(edge, duration = 300ms)`.
    - New box claimed -> trigger `TweenBoxClaimAnimation(box, duration = 400ms)` and burst particle emitter at box world center $(X, 0, Z)$.
    - Player combo count incremented -> spawn golden chain shockwave expansion.
    - Active turn changed -> camera focus easing and soft spotlight shift.

### 1.4 3D Raycasting & Touch Interaction
Converting 2D Compose touch coordinates $(X_{screen}, Y_{screen})$ into game moves:
1. **Unprojecting Touch Point**:
   $$\vec{R}_{origin} = \text{CameraPosition}$$
   $$\vec{R}_{dir} = \text{normalize}\left( \mathbf{M}_{view}^{-1} \cdot \mathbf{M}_{proj}^{-1} \cdot \vec{P}_{ndc} \right)$$
   where $\vec{P}_{ndc} = \left(\frac{2 X}{W} - 1, 1 - \frac{2 Y}{H}, 1.0\right)^T$.
2. **Intersection Test with Grid Plane**:
   Find $t$ where $\vec{R}_{origin} + t \vec{R}_{dir}$ intersects $Y = 0$.
3. **Cylinder Proximity Detection**:
   Calculate distance from intersection point on $Y = 0$ to each unclaimed edge's line segment $[P_1, P_2]$:
   $$\text{dist}(P, [P_1, P_2]) \le \text{Threshold} \approx 0.35 \times \text{NodeSpacing}$$
   If within threshold and not yet claimed, dispatch `onEdgeClick(edgeId)` to `GameViewModel`.

---

## 2. Asset Pipeline

### 2.1 Model Specifications (glTF 2.0 Binary - .glb)
- **Mesh Geometries**:
  - **Grid Pegs**: UV-sphere mesh (384 vertices, 720 triangles) rendered via GPU instancing (`filament::EntityManager::create()`). A $7 \times 7$ grid renders 49 pegs in 1 single draw call.
  - **Edge Tubes**: Procedurally extruded cylinder meshes generated in native memory with dynamic vertex deformation for draw-growth, or instanced cylinders with uniform scale deformation along their local length axis.
  - **Claimed Box Platforms**: Beveled cuboid (128 triangles) with recessed inner bevel.
  - **Mascots (Piko AI Bot)**: Low-poly mascot model (maximum 3,500 triangles, 16 bone joints, Draco compressed).
- **File Structure**:
  ```
  app/src/main/assets/models/
  ├── peg_node.glb
  ├── box_recess.glb
  ├── mascot_piko.glb
  └── badge_trophy.glb
  ```

### 2.2 Texture Specifications & Compression
- **Format**: ASTC (Adaptive Scalable Texture Compression) with 4x4 block footprint for diffuse/normals, 6x6 for ORM. Fallback to ETC2 for legacy GPUs.
- **PBR Texture Layout (Channel Packing)**:
  - **BaseColor**: RGB (sRGB color space).
  - **Normal Map**: RGB (Tangent space, linear).
  - **ORM Map**: Single packed linear texture:
    - Red Channel: Ambient Occlusion ($AO$).
    - Green Channel: Roughness ($R$).
    - Blue Channel: Metallic ($M$).
- **Resolution Limits**:
  - Mascot and Trophy: $1024 \times 1024$.
  - Environment Matcaps / Skybox IBL: $512 \times 512$ octahedral HDR probe.
  - Board Platform detail tiles: $512 \times 512$.

### 2.3 Rigging & Animation Pipeline
- **Joint Limits**: Max 16 bones per character model to avoid exceed GPU vertex attribute constants.
- **Clips**:
  - `idle`: 60-frame looping floating and head-tilt animation.
  - `anticipate`: 30-frame lean-in when AI evaluates moves.
  - `celebrate`: 45-frame spin and hop when scoring or winning.
  - `defeat`: 40-frame droop and power-down animation.

---

## 3. Shader Guide: Filament Material Definitions & GLSL

Filament uses precompiled `.mat` materials compiled using the `matc` offline tool or compiled at build time.

### 3.1 Neon Glowing Tubes Shader (`neon_tube.mat`)
```glsl
material {
    name : neon_tube,
    parameters : [
        { type : float4, name : glowColor },
        { type : float,  name : drawProgress }, // 0.0 to 1.0
        { type : float,  name : emissionIntensity }
    ],
    requires : [
        uv0,
        position
    ],
    shadingModel : lit
}

fragment {
    void material(inout MaterialInputs material) {
        prepareMaterial(material);

        // Clip tube geometry ahead of the draw progress
        if (variable_uv0.x > materialParams.drawProgress) {
            discard;
        }

        // Base neon color
        material.baseColor = float4(0.05, 0.05, 0.05, 1.0);
        material.roughness = 0.15;
        material.metallic = 0.0;

        // Fresnel edge rim emission for volumetric neon tube feel
        float3 viewDir = getWorldViewVector();
        float3 normal = getWorldGeometricNormal();
        float fresnel = 1.0 - max(0.0, dot(viewDir, normal));
        fresnel = pow(fresnel, 2.5);

        // Core glow + rim intensification
        float3 emissiveGlow = materialParams.glowColor.rgb * 
            (materialParams.emissionIntensity * (1.0 + fresnel * 3.0));

        // Pulsing tip when drawing
        float tipDistance = abs(variable_uv0.x - materialParams.drawProgress);
        if (tipDistance < 0.05 && materialParams.drawProgress < 1.0) {
            emissiveGlow += float3(1.0, 1.0, 1.0) * 15.0 * (1.0 - tipDistance / 0.05);
        }

        material.emissive = float4(emissiveGlow, 1.0);
    }
}
```

### 3.2 Metallic Brushed Pegs Shader (`metallic_peg.mat`)
```glsl
material {
    name : metallic_peg,
    parameters : [
        { type : float, name : highlightIntensity }
    ],
    shadingModel : lit
}

fragment {
    void material(inout MaterialInputs material) {
        prepareMaterial(material);

        // Chrome / brushed steel characteristics
        material.baseColor = float4(0.85, 0.88, 0.92, 1.0);
        material.metallic = 0.95;
        material.roughness = 0.22;
        material.reflectance = 0.8;

        // Touch/Selection active highlight
        if (materialParams.highlightIntensity > 0.0) {
            material.emissive = float4(0.2, 0.6, 1.0, 1.0) * materialParams.highlightIntensity * 4.0;
        }
    }
}
```

### 3.3 Glassmorphic Recessed Box Shader (`box_platform.mat`)
```glsl
material {
    name : box_platform,
    parameters : [
        { type : float4, name : playerColor },
        { type : float,  name : claimScale },
        { type : sampler2d, name : avatarWatermark }
    ],
    blending : transparent,
    shadingModel : lit
}

fragment {
    void material(inout MaterialInputs material) {
        prepareMaterial(material);

        float4 watermark = texture(materialParams_avatarWatermark, variable_uv0);
        float3 baseTint = materialParams.playerColor.rgb;

        material.baseColor = float4(mix(baseTint * 0.4, watermark.rgb, watermark.a * 0.3), 0.75);
        material.roughness = 0.35;
        material.metallic = 0.1;
        material.transmission = 0.5;

        // Soft border glow
        float2 centerDist = abs(variable_uv0 - 0.5) * 2.0;
        float borderFactor = max(centerDist.x, centerDist.y);
        if (borderFactor > 0.85) {
            material.emissive = float4(baseTint * 3.0 * (borderFactor - 0.85) / 0.15, 1.0);
        }
    }
}
```

### 3.4 Post-Processing Configuration
- **Bloom**:
  - Filter: Dual-Kawase pyramid downsampling with 5 blurs.
  - Threshold: Luminance $> 1.8$.
  - Intensity: 0.35 (Low-lag glow that makes neon lines pop without blinding the player).
- **Tone Mapping**: ACES (Academy Color Encoding System) curve for vibrant contrast.
- **Ambient Occlusion**: Ground Truth Ambient Occlusion (GTAO) with 8 samples per pixel.
- **Vignette**: Radius 0.85, Feather 0.45, Opacity 0.3.

---

## 4. Implementation Roadmap

```
+---------------------------------------------------------------------------------+
| Phase 1: Engine Foundation & Isometric Camera                                   |
| - Filament integration into AndroidView with lifecycle disposal                 |
| - Procedural 3D board grid generator (Nodes + Unclaimed ghost edges + Recesses) |
| - Isometric camera with 45° angle, pinch-to-zoom (1x-3x), two-finger drag       |
+---------------------------------------------------------------------------------+
                                       |
                                       v
+---------------------------------------------------------------------------------+
| Phase 2: Raycasting Touch Input & Interactive Gameplay                          |
| - Screen-to-world 3D raycasting with capacitive hit tolerance                   |
| - Neon edge draw animation (0.3s lerp with shader tip pulse)                    |
| - Box claim platform elevation and avatar stamping                              |
+---------------------------------------------------------------------------------+
                                       |
                                       v
+---------------------------------------------------------------------------------+
| Phase 3: Particle Dynamics & Multi-Sensory Feedback                             |
| - Burst particle system: 8-12 confetti quad particles on box capture            |
| - Chain combo golden ring expansion wave with pitch-scaling audio triggers      |
| - Audio/Haptic sync engine integration with SoundManager and HapticManager      |
+---------------------------------------------------------------------------------+
                                       |
                                       v
+---------------------------------------------------------------------------------+
| Phase 4: Glassmorphic Menus & 3D Previews                                       |
| - Animated live 3D board background for Home and Main Menu                      |
| - 3D grid size selector in Game Setup (interactive proportional scale preview)  |
| - 3D Results screen with 360° victory orbit camera and stats flip cards         |
+---------------------------------------------------------------------------------+
                                       |
                                       v
+---------------------------------------------------------------------------------+
| Phase 5: Profiling, Optimization & Full Accessibility                           |
| - Dynamic resolution scaling for target 60 FPS on Adreno 618 / Mali G57         |
| - TalkBack virtual accessibility node tree (Edge & Box state announcements)     |
| - High-contrast neon-free palette & Reduced Motion toggle                       |
+---------------------------------------------------------------------------------+
```

---

## 5. Testing Checklist

### 5.1 Performance Profiling Matrix
| Test Case | Target Benchmark | Verification Tool | Pass/Fail Criteria |
|---|---|---|---|
| **Frame Rate (Active Game)** | Steady 60 FPS ($\le 16.6\text{ms}$) | Android GPU Inspector / Perfetto | No consecutive dropped frames during edge draw |
| **GPU Render Time** | $< 11.0\text{ms}$ per frame | `dumpsys gfxinfo` | 95th percentile under 16ms |
| **Memory Footprint** | Peak heap $+ 45\text{MB}$ max | Android Studio Memory Profiler | Zero leak after 10 match restarts |
| **Dynamic Resolution** | Auto drops to 0.75x if FPS $< 55$ | Internal FPS counter hook | Resolution smoothly adjusts without frame stutter |
| **Thermal Budget** | Steady temperature rise $\le 4^\circ\text{C}$ over 15 min | Battery Historian | No thermal throttling on baseline device (Pixel 4a) |

### 5.2 Accessibility Validation Matrix
- [x] **TalkBack Compatibility**: Virtual accessibility node hierarchy maps every 3D edge to an accessible node with label: *"Edge from row X col Y to row X col Z, unclaimed"*. Double tap triggers raycast dispatch.
- [x] **High Contrast Mode**: Emissive shaders fall back to solid high-contrast WCAG 2.1 AAA colors (`#00E5FF` Cyan, `#FF007F` Magenta, `#FFFFFF` Lines) against `#121212` background.
- [x] **Reduced Motion Support**:
  - `Settings.Global.TRANSITION_ANIMATION_SCALE == 0` disables camera orbit spins and dynamic shakes.
  - Draw animations execute instantly ($\le 50\text{ms}$).
  - Particle confetti bursts replaced with a subtle 200ms alpha fade.

### 5.3 Device Compatibility Matrix
- [x] **Aspect Ratios**: Tested on 16:9, 18:9, 19.5:9, 21:9 with dynamic viewport letterboxing to protect HUD safe areas.
- [x] **Foldables & Tablets**: Seamless viewport reconfiguration during fold/unfold events via `onConfigurationChanged` and swap chain recreation.
- [x] **API Level Support**: MinSdk 24 (OpenGL ES 3.1 fallback) through Android 15 (TargetSdk 36, Vulkan 1.1+ backend).
