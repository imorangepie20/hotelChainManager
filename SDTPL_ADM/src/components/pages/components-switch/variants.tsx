"use client"

import * as React from "react"
import { Sun, Moon, Bell } from "lucide-react"

import { Switch } from "@/components/ui/switch"
import { Label } from "@/components/ui/label"
import { Card } from "@/components/ui/card"
import { Separator } from "@/components/ui/separator"
import { cn } from "@/lib/utils"

// ─── 1. Basic ─────────────────────────────────────────────────────────────────
export function BasicVariant() {
  const [checked, setChecked] = React.useState(false)
  return (
    <Switch checked={checked} onCheckedChange={setChecked} />
  )
}

// ─── 2. Airplane mode ─────────────────────────────────────────────────────────
export function AirplaneModeVariant() {
  const [checked, setChecked] = React.useState(false)
  return (
    <div className="flex items-center gap-2" data-testid="switch-airplane">
      <Switch
        id="switch-airplane-mode"
        checked={checked}
        onCheckedChange={setChecked}
      />
      <Label htmlFor="switch-airplane-mode">Airplane Mode</Label>
    </div>
  )
}

// ─── 3. Label with sublabel ──────────────────────────────────────────────────
export function LabelWithSublabelVariant() {
  const [checked, setChecked] = React.useState(false)
  return (
    <div className="flex items-center justify-between gap-4">
      <div className="flex flex-col gap-0.5">
        <Label htmlFor="switch-marketing">Marketing emails</Label>
        <p className="text-xs text-muted-foreground">
          A short description goes here.
        </p>
      </div>
      <Switch
        id="switch-marketing"
        checked={checked}
        onCheckedChange={setChecked}
      />
    </div>
  )
}

// ─── 4. Disabled ──────────────────────────────────────────────────────────────
export function DisabledVariant() {
  return (
    <div className="flex items-center gap-6">
      <div className="flex items-center gap-2">
        <Switch id="disabled-off" disabled defaultChecked={false} />
        <Label htmlFor="disabled-off" className="opacity-50">Off</Label>
      </div>
      <div className="flex items-center gap-2">
        <Switch id="disabled-on" disabled defaultChecked={true} />
        <Label htmlFor="disabled-on" className="opacity-50">On</Label>
      </div>
    </div>
  )
}

// ─── 5. Sizes ─────────────────────────────────────────────────────────────────
export function SizesVariant() {
  const [sm, setSm] = React.useState(true)
  const [md, setMd] = React.useState(true)
  const [lg, setLg] = React.useState(true)
  return (
    <div className="flex items-center gap-6">
      <div className="flex flex-col items-center gap-1.5">
        {/* Small: override track + thumb via className */}
        <Switch
          checked={sm}
          onCheckedChange={setSm}
          className="h-4 w-7 [&_[data-slot=switch-thumb]]:size-3 [&_[data-slot=switch-thumb]]:data-checked:translate-x-[calc(100%-1px)]"
        />
        <span className="text-xs text-muted-foreground">Small</span>
      </div>
      <div className="flex flex-col items-center gap-1.5">
        <Switch checked={md} onCheckedChange={setMd} />
        <span className="text-xs text-muted-foreground">Default</span>
      </div>
      <div className="flex flex-col items-center gap-1.5">
        {/* Large: bigger track + thumb */}
        <Switch
          checked={lg}
          onCheckedChange={setLg}
          className="h-7 w-12 [&_[data-slot=switch-thumb]]:size-5 [&_[data-slot=switch-thumb]]:data-checked:translate-x-[calc(100%+1px)]"
        />
        <span className="text-xs text-muted-foreground">Large</span>
      </div>
    </div>
  )
}

// ─── 6. Colored ───────────────────────────────────────────────────────────────
export function ColoredVariant() {
  const [emerald, setEmerald] = React.useState(true)
  const [blue, setBlue] = React.useState(true)
  const [rose, setRose] = React.useState(true)
  return (
    <div className="flex items-center gap-6">
      <div className="flex items-center gap-2">
        <Switch
          checked={emerald}
          onCheckedChange={setEmerald}
          className="data-checked:bg-emerald-500"
        />
        <Label>Emerald</Label>
      </div>
      <div className="flex items-center gap-2">
        <Switch
          checked={blue}
          onCheckedChange={setBlue}
          className="data-checked:bg-blue-500"
        />
        <Label>Blue</Label>
      </div>
      <div className="flex items-center gap-2">
        <Switch
          checked={rose}
          onCheckedChange={setRose}
          className="data-checked:bg-rose-500"
        />
        <Label>Rose</Label>
      </div>
    </div>
  )
}

// ─── 7. With icons ────────────────────────────────────────────────────────────
export function WithIconsVariant() {
  const [isDark, setIsDark] = React.useState(false)
  return (
    <div className="flex items-center gap-3">
      <Sun
        className={cn(
          "size-4 transition-colors",
          isDark ? "text-muted-foreground" : "text-amber-500"
        )}
      />
      <Switch checked={isDark} onCheckedChange={setIsDark} />
      <Moon
        className={cn(
          "size-4 transition-colors",
          isDark ? "text-indigo-400" : "text-muted-foreground"
        )}
      />
    </div>
  )
}

// ─── 8. Square ────────────────────────────────────────────────────────────────
export function SquareVariant() {
  const [checked, setChecked] = React.useState(false)
  return (
    <div className="flex items-center gap-2">
      <Switch
        id="switch-square"
        checked={checked}
        onCheckedChange={setChecked}
        className="rounded-md [&_[data-slot=switch-thumb]]:rounded-sm"
      />
      <Label htmlFor="switch-square">Square corners</Label>
    </div>
  )
}

// ─── 9. Settings list ─────────────────────────────────────────────────────────
const SETTINGS_ITEMS = [
  {
    id: "sw-push",
    label: "Push notifications",
    description: "Receive alerts on your device.",
    defaultOn: true,
  },
  {
    id: "sw-email",
    label: "Email updates",
    description: "Get weekly digest emails.",
    defaultOn: false,
  },
  {
    id: "sw-2fa",
    label: "Two-factor auth",
    description: "Secure your account with 2FA.",
    defaultOn: false,
  },
]

export function SettingsListVariant() {
  const [enabled, setEnabled] = React.useState<Record<string, boolean>>(
    Object.fromEntries(SETTINGS_ITEMS.map((s) => [s.id, s.defaultOn]))
  )

  function toggle(id: string) {
    setEnabled((prev) => ({ ...prev, [id]: !prev[id] }))
  }

  return (
    <div className="flex flex-col">
      {SETTINGS_ITEMS.map((item, idx) => (
        <React.Fragment key={item.id}>
          {idx > 0 && <Separator />}
          <div className="flex items-center justify-between gap-4 py-3">
            <div className="flex flex-col gap-0.5">
              <Label htmlFor={item.id}>{item.label}</Label>
              <p className="text-xs text-muted-foreground">{item.description}</p>
            </div>
            <Switch
              id={item.id}
              checked={enabled[item.id]}
              onCheckedChange={() => toggle(item.id)}
            />
          </div>
        </React.Fragment>
      ))}
    </div>
  )
}

// ─── 10. Card ─────────────────────────────────────────────────────────────────
export function CardVariant() {
  const [enabled, setEnabled] = React.useState(false)
  return (
    <Card
      className={cn(
        "p-4 transition-colors",
        enabled
          ? "ring-2 ring-primary"
          : "ring-1 ring-foreground/10"
      )}
    >
      <div className="flex items-start justify-between gap-4">
        <div className="flex flex-col gap-1">
          <div className="flex items-center gap-2 text-sm font-medium">
            <Bell className="size-4" />
            Notifications
          </div>
          <p className="text-xs text-muted-foreground">
            Enable to receive real-time alerts.
          </p>
        </div>
        <Switch checked={enabled} onCheckedChange={setEnabled} />
      </div>
    </Card>
  )
}
