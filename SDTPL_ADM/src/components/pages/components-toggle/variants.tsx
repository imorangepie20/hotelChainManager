"use client"

import * as React from "react"
import {
  Bold,
  Italic,
  Underline,
  Star,
  Heart,
  Bookmark,
  AlignLeft,
  AlignCenter,
  AlignRight,
} from "lucide-react"

import { Toggle } from "@/components/ui/toggle"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"

// ─── 1. Basic ─────────────────────────────────────────────────────────────────
export function BasicVariant() {
  const [pressed, setPressed] = React.useState(false)
  return (
    <div data-testid="toggle-basic">
      <Toggle
        aria-label="Toggle bold"
        pressed={pressed}
        onPressedChange={setPressed}
        className="font-bold"
      >
        <Bold />
        Bold
      </Toggle>
    </div>
  )
}

// ─── 2. With icon ─────────────────────────────────────────────────────────────
export function WithIconVariant() {
  const [pressed, setPressed] = React.useState(false)
  return (
    <Toggle
      aria-label="Toggle italic"
      pressed={pressed}
      onPressedChange={setPressed}
    >
      <Italic />
    </Toggle>
  )
}

// ─── 3. With text + icon ──────────────────────────────────────────────────────
export function WithTextIconVariant() {
  const [pressed, setPressed] = React.useState(false)
  return (
    <Toggle
      aria-label="Toggle bold text"
      pressed={pressed}
      onPressedChange={setPressed}
      className="font-semibold"
    >
      <Bold />
      Bold
    </Toggle>
  )
}

// ─── 4. Outline ───────────────────────────────────────────────────────────────
export function OutlineVariant() {
  return (
    <div className="flex items-center gap-2">
      <OutlineToggle aria-label="Toggle star" icon={<Star />} />
      <OutlineToggle aria-label="Toggle heart" icon={<Heart />} />
      <OutlineToggle aria-label="Toggle bookmark" icon={<Bookmark />} />
    </div>
  )
}

function OutlineToggle({
  icon,
  "aria-label": ariaLabel,
}: {
  icon: React.ReactNode
  "aria-label": string
}) {
  const [pressed, setPressed] = React.useState(false)
  return (
    <Toggle
      variant="outline"
      aria-label={ariaLabel}
      pressed={pressed}
      onPressedChange={setPressed}
    >
      {icon}
    </Toggle>
  )
}

// ─── 5. Sizes ─────────────────────────────────────────────────────────────────
export function SizesVariant() {
  return (
    <div className="flex items-center gap-3">
      <div className="flex flex-col items-center gap-1.5">
        <Toggle size="sm" aria-label="Small toggle bold" defaultPressed={false}>
          <Bold />
        </Toggle>
        <span className="text-xs text-muted-foreground">Small</span>
      </div>
      <div className="flex flex-col items-center gap-1.5">
        <Toggle size="default" aria-label="Default toggle bold" defaultPressed={false}>
          <Bold />
        </Toggle>
        <span className="text-xs text-muted-foreground">Default</span>
      </div>
      <div className="flex flex-col items-center gap-1.5">
        <Toggle size="lg" aria-label="Large toggle bold" defaultPressed={false}>
          <Bold />
        </Toggle>
        <span className="text-xs text-muted-foreground">Large</span>
      </div>
    </div>
  )
}

// ─── 6. Disabled ──────────────────────────────────────────────────────────────
export function DisabledVariant() {
  return (
    <div className="flex items-center gap-3">
      <div className="flex flex-col items-center gap-1.5">
        <Toggle aria-label="Disabled unpressed" disabled>
          <Underline />
        </Toggle>
        <span className="text-xs text-muted-foreground">Disabled</span>
      </div>
      <div className="flex flex-col items-center gap-1.5">
        <Toggle aria-label="Disabled pressed" pressed disabled>
          <Underline />
        </Toggle>
        <span className="text-xs text-muted-foreground">Disabled (pressed)</span>
      </div>
    </div>
  )
}

// ─── 7. Toggle group ──────────────────────────────────────────────────────────
export function ToggleGroupVariant() {
  const [formatting, setFormatting] = React.useState<string[]>([])
  const [alignment, setAlignment] = React.useState<string[]>(["left"])

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-col gap-1.5">
        <span className="text-xs text-muted-foreground">Formatting (multiple)</span>
        <ToggleGroup
          multiple
          value={formatting}
          onValueChange={setFormatting}
          aria-label="Text formatting"
          spacing={0}
        >
          <ToggleGroupItem value="bold" aria-label="Toggle bold">
            <Bold />
          </ToggleGroupItem>
          <ToggleGroupItem value="italic" aria-label="Toggle italic">
            <Italic />
          </ToggleGroupItem>
          <ToggleGroupItem value="underline" aria-label="Toggle underline">
            <Underline />
          </ToggleGroupItem>
        </ToggleGroup>
      </div>
      <div className="flex flex-col gap-1.5">
        <span className="text-xs text-muted-foreground">Alignment (single)</span>
        <ToggleGroup
          value={alignment}
          onValueChange={setAlignment}
          aria-label="Text alignment"
          spacing={0}
        >
          <ToggleGroupItem value="left" aria-label="Align left">
            <AlignLeft />
          </ToggleGroupItem>
          <ToggleGroupItem value="center" aria-label="Align center">
            <AlignCenter />
          </ToggleGroupItem>
          <ToggleGroupItem value="right" aria-label="Align right">
            <AlignRight />
          </ToggleGroupItem>
        </ToggleGroup>
      </div>
    </div>
  )
}
