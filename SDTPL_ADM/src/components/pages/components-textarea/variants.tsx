"use client"

import * as React from "react"

import { Textarea } from "@/components/ui/textarea"
import { Label } from "@/components/ui/label"
import { Button } from "@/components/ui/button"
import { Avatar, AvatarFallback } from "@/components/ui/avatar"
import { Send, AlertCircle } from "lucide-react"

// ─── 1. Basic ─────────────────────────────────────────────────────────────────
export function BasicVariant() {
  return <Textarea placeholder="Type your message here." />
}

// ─── 2. With Label ────────────────────────────────────────────────────────────
export function WithLabelVariant() {
  return (
    <div className="flex flex-col gap-1.5">
      <Label htmlFor="textarea-with-label">Your message</Label>
      <Textarea id="textarea-with-label" placeholder="Type your message here." />
    </div>
  )
}

// ─── 3. Required ──────────────────────────────────────────────────────────────
export function RequiredVariant() {
  return (
    <div className="flex flex-col gap-1.5">
      <Label htmlFor="textarea-required">
        Bio <span aria-hidden="true">*</span>
      </Label>
      <Textarea
        id="textarea-required"
        placeholder="Tell us about yourself…"
        required
      />
      <p className="text-xs text-muted-foreground">Required</p>
    </div>
  )
}

// ─── 4. Helper Text ───────────────────────────────────────────────────────────
export function HelperTextVariant() {
  return (
    <div className="flex flex-col gap-1.5">
      <Textarea placeholder="Add details…" />
      <p className="text-xs text-muted-foreground">
        Please add as many details as you can.
      </p>
    </div>
  )
}

// ─── 5. Error State ───────────────────────────────────────────────────────────
export function ErrorStateVariant() {
  return (
    <div className="flex flex-col gap-1.5">
      <Textarea
        aria-invalid="true"
        placeholder="Type your message here."
        defaultValue=""
      />
      <p className="flex items-center gap-1.5 text-xs text-destructive">
        <AlertCircle className="size-3.5 shrink-0" />
        Message should be at least 10 characters.
      </p>
    </div>
  )
}

// ─── 6. Character Counter ─────────────────────────────────────────────────────
export function CharacterCounterVariant() {
  const MAX = 200
  const [value, setValue] = React.useState("")

  return (
    <div className="flex flex-col gap-1.5" data-testid="textarea-counter">
      <Textarea
        maxLength={MAX}
        value={value}
        onChange={(e) => setValue(e.target.value)}
        placeholder="Type your message here."
      />
      <p className="text-xs text-muted-foreground text-right">
        <span data-testid="textarea-count">{value.length}</span>/{MAX}
      </p>
    </div>
  )
}

// ─── 7. Auto-grow ─────────────────────────────────────────────────────────────
export function AutoGrowVariant() {
  const [value, setValue] = React.useState("")
  const rows = Math.min(10, Math.max(3, value.split("\n").length))

  return (
    <Textarea
      rows={rows}
      value={value}
      onChange={(e) => setValue(e.target.value)}
      placeholder="Start typing — the textarea will grow…"
      className="resize-none"
    />
  )
}

// ─── 8. With Button (comment) ─────────────────────────────────────────────────
export function WithButtonVariant() {
  return (
    <div className="flex flex-col gap-2">
      <Textarea placeholder="Write a comment…" />
      <div className="flex justify-end">
        <Button size="sm">
          <Send className="size-3.5" />
          Send
        </Button>
      </div>
    </div>
  )
}

// ─── 9. Disabled ──────────────────────────────────────────────────────────────
export function DisabledVariant() {
  return (
    <Textarea
      disabled
      value="This textarea is disabled and cannot be edited."
      readOnly
    />
  )
}

// ─── 10. Avatar Comment ───────────────────────────────────────────────────────
export function AvatarCommentVariant() {
  return (
    <div className="flex gap-3">
      <Avatar>
        <AvatarFallback>PJ</AvatarFallback>
      </Avatar>
      <div className="flex flex-1 flex-col gap-2">
        <Textarea placeholder="Write a comment…" />
        <div className="flex justify-end">
          <Button size="sm">Post</Button>
        </div>
      </div>
    </div>
  )
}

// ─── 11. Resizable / Fixed ────────────────────────────────────────────────────
export function ResizableFixedVariant() {
  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-col gap-1.5">
        <p className="text-xs text-muted-foreground">Fixed (no resize)</p>
        <Textarea
          placeholder="This textarea cannot be resized."
          className="resize-none"
        />
      </div>
      <div className="flex flex-col gap-1.5">
        <p className="text-xs text-muted-foreground">Resizable (vertical)</p>
        <Textarea
          placeholder="Drag the corner to resize."
          className="resize-y"
        />
      </div>
    </div>
  )
}
