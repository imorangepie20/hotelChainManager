"use client"

import { Bold, Italic, Underline, Info } from "lucide-react"

import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip"
import { Button } from "@/components/ui/button"

// ─── 1. Basic ─────────────────────────────────────────────────────────────────
export function BasicVariant() {
  return (
    <div data-testid="tooltip-basic">
      <TooltipProvider>
        <Tooltip>
          <TooltipTrigger render={<Button variant="outline">Hover</Button>} />
          <TooltipContent>Add to library</TooltipContent>
        </Tooltip>
      </TooltipProvider>
    </div>
  )
}

// ─── 2. Positions ─────────────────────────────────────────────────────────────
export function PositionsVariant() {
  const positions = ["top", "right", "bottom", "left"] as const

  return (
    <TooltipProvider>
      <div className="flex flex-wrap gap-2">
        {positions.map((side) => (
          <Tooltip key={side}>
            <TooltipTrigger
              render={
                <Button variant="outline" size="sm" className="capitalize">
                  {side.charAt(0).toUpperCase() + side.slice(1)}
                </Button>
              }
            />
            <TooltipContent side={side}>On {side}</TooltipContent>
          </Tooltip>
        ))}
      </div>
    </TooltipProvider>
  )
}

// ─── 3. With title ────────────────────────────────────────────────────────────
export function WithTitleVariant() {
  return (
    <TooltipProvider>
      <Tooltip>
        <TooltipTrigger render={<Button variant="outline">Info</Button>} />
        <TooltipContent className="max-w-48 flex-col items-start gap-1">
          <p className="font-semibold">Keyboard shortcut</p>
          <p className="text-xs opacity-80">
            Press the key combination to trigger this action instantly.
          </p>
        </TooltipContent>
      </Tooltip>
    </TooltipProvider>
  )
}

// ─── 4. Stats ─────────────────────────────────────────────────────────────────
export function StatsVariant() {
  return (
    <TooltipProvider>
      <Tooltip>
        <TooltipTrigger
          render={
            <Button variant="ghost" size="icon" aria-label="View stats">
              <Info className="size-4" />
            </Button>
          }
        />
        <TooltipContent className="flex-col items-start gap-1">
          <p className="font-semibold">Total: 1,234</p>
          <p className="text-xs opacity-80">+12% this week</p>
        </TooltipContent>
      </Tooltip>
    </TooltipProvider>
  )
}

// ─── 5. Icon buttons ──────────────────────────────────────────────────────────
export function IconButtonsVariant() {
  const tools = [
    { icon: Bold, label: "Bold" },
    { icon: Italic, label: "Italic" },
    { icon: Underline, label: "Underline" },
  ] as const

  return (
    <TooltipProvider>
      <div className="flex gap-1">
        {tools.map(({ icon: Icon, label }) => (
          <Tooltip key={label}>
            <TooltipTrigger
              render={
                <Button variant="outline" size="icon" aria-label={label}>
                  <Icon className="size-4" />
                </Button>
              }
            />
            <TooltipContent>{label}</TooltipContent>
          </Tooltip>
        ))}
      </div>
    </TooltipProvider>
  )
}

// ─── 6. With shortcut ─────────────────────────────────────────────────────────
export function WithShortcutVariant() {
  return (
    <TooltipProvider>
      <Tooltip>
        <TooltipTrigger render={<Button variant="outline">Save</Button>} />
        <TooltipContent className="gap-2">
          <span>Save</span>
          <span
            data-slot="kbd"
            className="rounded border border-background/30 bg-background/20 px-1 py-0.5 font-mono text-[10px] text-background"
          >
            ⌘S
          </span>
        </TooltipContent>
      </Tooltip>
    </TooltipProvider>
  )
}

// ─── 7. Rich preview ──────────────────────────────────────────────────────────
export function RichPreviewVariant() {
  return (
    <TooltipProvider>
      <Tooltip>
        <TooltipTrigger
          render={<Button variant="outline">Preview product</Button>}
        />
        <TooltipContent className="w-52 flex-col items-start gap-0 overflow-hidden p-0">
          <div className="h-24 w-full bg-gradient-to-br from-violet-500 to-indigo-600" />
          <div className="flex flex-col gap-0.5 p-3">
            <p className="font-semibold">Wireless Headphones</p>
            <p className="text-xs opacity-80">$129.99 — Free shipping</p>
          </div>
        </TooltipContent>
      </Tooltip>
    </TooltipProvider>
  )
}

// ─── 8. No arrow ──────────────────────────────────────────────────────────────
export function NoArrowVariant() {
  return (
    <TooltipProvider>
      <Tooltip>
        <TooltipTrigger
          render={<Button variant="outline">No arrow</Button>}
        />
        <TooltipContent className="[&_[data-part=arrow]]:hidden">
          Plain tooltip, no arrow
        </TooltipContent>
      </Tooltip>
    </TooltipProvider>
  )
}
