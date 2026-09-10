"use client"

import * as React from "react"

import {
  Tabs,
  TabsList,
  TabsTrigger,
  TabsContent,
} from "@/components/ui/tabs"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Card } from "@/components/ui/card"
import { cn } from "@/lib/utils"
import {
  LayoutDashboard,
  BarChart3,
  FileText,
  Settings,
  Home,
  Folder,
  Package,
} from "lucide-react"

// ─── 1. Basic ─────────────────────────────────────────────────────────────────
export function BasicVariant() {
  return (
    <div data-testid="tabs-basic">
      <Tabs defaultValue="overview">
        <TabsList>
          <TabsTrigger value="overview">Overview</TabsTrigger>
          <TabsTrigger value="analytics">Analytics</TabsTrigger>
          <TabsTrigger value="reports">Reports</TabsTrigger>
        </TabsList>
        <TabsContent value="overview" className="mt-3">
          <p className="text-sm text-muted-foreground">
            Welcome to the overview. Here you&apos;ll find a summary of your
            project&apos;s key metrics and recent activity.
          </p>
        </TabsContent>
        <TabsContent value="analytics" className="mt-3">
          <p className="text-sm text-muted-foreground">
            Analytics shows your traffic trends, user engagement, and
            conversion rates over time.
          </p>
        </TabsContent>
        <TabsContent value="reports" className="mt-3">
          <p className="text-sm text-muted-foreground">
            Reports give you exportable summaries of your data including
            weekly and monthly breakdowns.
          </p>
        </TabsContent>
      </Tabs>
    </div>
  )
}

// ─── 2. Underline ─────────────────────────────────────────────────────────────
export function UnderlineVariant() {
  return (
    <Tabs defaultValue="overview">
      <TabsList className="h-auto w-full justify-start gap-0 rounded-none border-b bg-transparent p-0">
        <TabsTrigger
          value="overview"
          className="relative h-9 rounded-none border-b-2 border-transparent px-4 pb-2 pt-2 font-medium text-muted-foreground shadow-none data-active:border-primary data-active:bg-transparent data-active:text-foreground data-active:shadow-none"
        >
          Overview
        </TabsTrigger>
        <TabsTrigger
          value="analytics"
          className="relative h-9 rounded-none border-b-2 border-transparent px-4 pb-2 pt-2 font-medium text-muted-foreground shadow-none data-active:border-primary data-active:bg-transparent data-active:text-foreground data-active:shadow-none"
        >
          Analytics
        </TabsTrigger>
        <TabsTrigger
          value="reports"
          className="relative h-9 rounded-none border-b-2 border-transparent px-4 pb-2 pt-2 font-medium text-muted-foreground shadow-none data-active:border-primary data-active:bg-transparent data-active:text-foreground data-active:shadow-none"
        >
          Reports
        </TabsTrigger>
      </TabsList>
      <TabsContent value="overview" className="mt-3">
        <p className="text-sm text-muted-foreground">
          A clean underline style that highlights the active tab with a
          bottom border indicator instead of a filled background.
        </p>
      </TabsContent>
      <TabsContent value="analytics" className="mt-3">
        <p className="text-sm text-muted-foreground">
          Analytics panel — underline variant shows traffic and engagement
          data without visual clutter.
        </p>
      </TabsContent>
      <TabsContent value="reports" className="mt-3">
        <p className="text-sm text-muted-foreground">
          Reports panel — exportable data tables and scheduled report
          configuration.
        </p>
      </TabsContent>
    </Tabs>
  )
}

// ─── 3. Pills ─────────────────────────────────────────────────────────────────
export function PillsVariant() {
  return (
    <Tabs defaultValue="overview">
      <TabsList className="h-auto gap-1 rounded-none bg-transparent p-0">
        <TabsTrigger
          value="overview"
          className="rounded-full border border-transparent px-4 py-1.5 text-sm data-active:border-primary data-active:bg-primary data-active:text-primary-foreground data-active:shadow-none"
        >
          Overview
        </TabsTrigger>
        <TabsTrigger
          value="analytics"
          className="rounded-full border border-transparent px-4 py-1.5 text-sm data-active:border-primary data-active:bg-primary data-active:text-primary-foreground data-active:shadow-none"
        >
          Analytics
        </TabsTrigger>
        <TabsTrigger
          value="reports"
          className="rounded-full border border-transparent px-4 py-1.5 text-sm data-active:border-primary data-active:bg-primary data-active:text-primary-foreground data-active:shadow-none"
        >
          Reports
        </TabsTrigger>
      </TabsList>
      <TabsContent value="overview" className="mt-3">
        <p className="text-sm text-muted-foreground">
          Pill-shaped tabs with a fully filled primary background on the
          active item.
        </p>
      </TabsContent>
      <TabsContent value="analytics" className="mt-3">
        <p className="text-sm text-muted-foreground">
          Analytics pill — compact and visually distinct from underline or
          segmented styles.
        </p>
      </TabsContent>
      <TabsContent value="reports" className="mt-3">
        <p className="text-sm text-muted-foreground">
          Reports pill — great for filters or compact navigation bars.
        </p>
      </TabsContent>
    </Tabs>
  )
}

// ─── 4. Vertical ──────────────────────────────────────────────────────────────
export function VerticalVariant() {
  return (
    <Tabs defaultValue="overview" orientation="vertical" className="flex gap-4">
      <TabsList className="h-fit w-36 flex-col items-stretch rounded-lg bg-muted p-1">
        <TabsTrigger value="overview" className="justify-start">
          Overview
        </TabsTrigger>
        <TabsTrigger value="analytics" className="justify-start">
          Analytics
        </TabsTrigger>
        <TabsTrigger value="reports" className="justify-start">
          Reports
        </TabsTrigger>
      </TabsList>
      <div className="flex-1">
        <TabsContent value="overview">
          <p className="text-sm text-muted-foreground">
            Vertical layout places the tab list in a left-side column,
            ideal for settings pages with many sections.
          </p>
        </TabsContent>
        <TabsContent value="analytics">
          <p className="text-sm text-muted-foreground">
            Analytics section in vertical mode — wide content area makes it
            easy to display charts and data tables.
          </p>
        </TabsContent>
        <TabsContent value="reports">
          <p className="text-sm text-muted-foreground">
            Reports in a vertical layout provide clear separation between
            navigation and content.
          </p>
        </TabsContent>
      </div>
    </Tabs>
  )
}

// ─── 5. With icons ────────────────────────────────────────────────────────────
export function WithIconsVariant() {
  return (
    <Tabs defaultValue="dashboard">
      <TabsList>
        <TabsTrigger value="dashboard">
          <LayoutDashboard />
          Dashboard
        </TabsTrigger>
        <TabsTrigger value="analytics">
          <BarChart3 />
          Analytics
        </TabsTrigger>
        <TabsTrigger value="reports">
          <FileText />
          Reports
        </TabsTrigger>
      </TabsList>
      <TabsContent value="dashboard" className="mt-3">
        <p className="text-sm text-muted-foreground">
          The dashboard gives you a bird&apos;s-eye view of your key metrics at a
          glance.
        </p>
      </TabsContent>
      <TabsContent value="analytics" className="mt-3">
        <p className="text-sm text-muted-foreground">
          Analytics breaks down traffic sources, session durations, and
          conversion funnels.
        </p>
      </TabsContent>
      <TabsContent value="reports" className="mt-3">
        <p className="text-sm text-muted-foreground">
          Reports let you schedule and export snapshots of your data.
        </p>
      </TabsContent>
    </Tabs>
  )
}

// ─── 6. Full width ────────────────────────────────────────────────────────────
export function FullWidthVariant() {
  return (
    <Tabs defaultValue="overview">
      <TabsList className="w-full">
        <TabsTrigger value="overview" className="flex-1">
          Overview
        </TabsTrigger>
        <TabsTrigger value="analytics" className="flex-1">
          Analytics
        </TabsTrigger>
        <TabsTrigger value="reports" className="flex-1">
          Reports
        </TabsTrigger>
      </TabsList>
      <TabsContent value="overview" className="mt-3">
        <p className="text-sm text-muted-foreground">
          Full-width tabs stretch to fill the entire container — great for
          mobile-first or constrained layouts.
        </p>
      </TabsContent>
      <TabsContent value="analytics" className="mt-3">
        <p className="text-sm text-muted-foreground">
          Analytics panel in full-width mode — each tab claims equal space
          regardless of label length.
        </p>
      </TabsContent>
      <TabsContent value="reports" className="mt-3">
        <p className="text-sm text-muted-foreground">
          Reports panel — full-width ensures a consistent, balanced look
          across all viewport sizes.
        </p>
      </TabsContent>
    </Tabs>
  )
}

// ─── 7. Boxed / card ──────────────────────────────────────────────────────────
export function BoxedVariant() {
  return (
    <Tabs defaultValue="overview">
      <TabsList className="h-auto gap-1 rounded-none bg-transparent p-0">
        <TabsTrigger
          value="overview"
          className="rounded-t-md border border-b-0 bg-muted px-4 py-2 text-sm data-active:bg-background data-active:text-foreground data-active:shadow-none"
        >
          Overview
        </TabsTrigger>
        <TabsTrigger
          value="analytics"
          className="rounded-t-md border border-b-0 bg-muted px-4 py-2 text-sm data-active:bg-background data-active:text-foreground data-active:shadow-none"
        >
          Analytics
        </TabsTrigger>
        <TabsTrigger
          value="reports"
          className="rounded-t-md border border-b-0 bg-muted px-4 py-2 text-sm data-active:bg-background data-active:text-foreground data-active:shadow-none"
        >
          Reports
        </TabsTrigger>
      </TabsList>
      <div className="rounded-b-md rounded-tr-md border p-4">
        <TabsContent value="overview">
          <p className="text-sm text-muted-foreground">
            Boxed tabs give a card-like appearance — the active tab appears
            to be part of the content panel below it.
          </p>
        </TabsContent>
        <TabsContent value="analytics">
          <p className="text-sm text-muted-foreground">
            Analytics in a boxed panel — clearly bounded content area
            reinforces the tab metaphor.
          </p>
        </TabsContent>
        <TabsContent value="reports">
          <p className="text-sm text-muted-foreground">
            Reports in the boxed variant — clean separation between the tab
            bar and content region.
          </p>
        </TabsContent>
      </div>
    </Tabs>
  )
}

// ─── 8. Disabled tab ──────────────────────────────────────────────────────────
export function DisabledTabVariant() {
  return (
    <Tabs defaultValue="active">
      <TabsList>
        <TabsTrigger value="active">Active</TabsTrigger>
        <TabsTrigger value="disabled" disabled>
          Disabled
        </TabsTrigger>
        <TabsTrigger value="other">Other</TabsTrigger>
      </TabsList>
      <TabsContent value="active" className="mt-3">
        <p className="text-sm text-muted-foreground">
          This tab is active and fully interactive. Click the other tabs to
          switch panels.
        </p>
      </TabsContent>
      <TabsContent value="disabled" className="mt-3">
        <p className="text-sm text-muted-foreground">
          This panel belongs to the disabled tab and cannot be activated by
          clicking.
        </p>
      </TabsContent>
      <TabsContent value="other" className="mt-3">
        <p className="text-sm text-muted-foreground">
          The Other tab is fully enabled — the middle tab is disabled and
          cannot be interacted with.
        </p>
      </TabsContent>
    </Tabs>
  )
}

// ─── 9. With badges ───────────────────────────────────────────────────────────
export function WithBadgesVariant() {
  return (
    <Tabs defaultValue="inbox">
      <TabsList>
        <TabsTrigger value="inbox" className="gap-2">
          Inbox
          <Badge variant="secondary" className="ml-1 text-xs">
            3
          </Badge>
        </TabsTrigger>
        <TabsTrigger value="drafts" className="gap-2">
          Drafts
          <Badge variant="secondary" className="ml-1 text-xs">
            0
          </Badge>
        </TabsTrigger>
        <TabsTrigger value="sent" className="gap-2">
          Sent
          <Badge variant="secondary" className="ml-1 text-xs">
            7
          </Badge>
        </TabsTrigger>
      </TabsList>
      <TabsContent value="inbox" className="mt-3">
        <p className="text-sm text-muted-foreground">
          You have 3 unread messages in your inbox waiting for your
          attention.
        </p>
      </TabsContent>
      <TabsContent value="drafts" className="mt-3">
        <p className="text-sm text-muted-foreground">
          No drafts saved. Start composing a new message to save it as a
          draft.
        </p>
      </TabsContent>
      <TabsContent value="sent" className="mt-3">
        <p className="text-sm text-muted-foreground">
          7 messages have been sent from this account in the last 30 days.
        </p>
      </TabsContent>
    </Tabs>
  )
}

// ─── 10. Segmented ────────────────────────────────────────────────────────────
export function SegmentedVariant() {
  return (
    <Tabs defaultValue="day">
      <TabsList className="inline-flex h-8 rounded-full bg-muted p-0.5">
        <TabsTrigger
          value="day"
          className="h-7 rounded-full px-3 text-xs data-active:bg-background data-active:shadow-sm"
        >
          Day
        </TabsTrigger>
        <TabsTrigger
          value="week"
          className="h-7 rounded-full px-3 text-xs data-active:bg-background data-active:shadow-sm"
        >
          Week
        </TabsTrigger>
        <TabsTrigger
          value="month"
          className="h-7 rounded-full px-3 text-xs data-active:bg-background data-active:shadow-sm"
        >
          Month
        </TabsTrigger>
        <TabsTrigger
          value="year"
          className="h-7 rounded-full px-3 text-xs data-active:bg-background data-active:shadow-sm"
        >
          Year
        </TabsTrigger>
      </TabsList>
      <TabsContent value="day" className="mt-3">
        <p className="text-sm text-muted-foreground">
          Daily view — showing activity for today broken down by hour.
        </p>
      </TabsContent>
      <TabsContent value="week" className="mt-3">
        <p className="text-sm text-muted-foreground">
          Weekly view — aggregated activity over the past 7 days.
        </p>
      </TabsContent>
      <TabsContent value="month" className="mt-3">
        <p className="text-sm text-muted-foreground">
          Monthly view — trends and comparisons over the current month.
        </p>
      </TabsContent>
      <TabsContent value="year" className="mt-3">
        <p className="text-sm text-muted-foreground">
          Yearly view — annual summary with month-over-month comparisons.
        </p>
      </TabsContent>
    </Tabs>
  )
}

// ─── 11. Scrollable ───────────────────────────────────────────────────────────
export function ScrollableVariant() {
  return (
    <Tabs defaultValue="overview">
      <div className="overflow-x-auto">
        <TabsList className="inline-flex w-max">
          <TabsTrigger value="overview">Overview</TabsTrigger>
          <TabsTrigger value="projects">Projects</TabsTrigger>
          <TabsTrigger value="packages">Packages</TabsTrigger>
          <TabsTrigger value="new">New</TabsTrigger>
          <TabsTrigger value="insights">Insights</TabsTrigger>
          <TabsTrigger value="settings">Settings</TabsTrigger>
        </TabsList>
      </div>
      <TabsContent value="overview" className="mt-3">
        <p className="text-sm text-muted-foreground">
          Overview — scroll the tab bar horizontally to reveal all
          navigation options.
        </p>
      </TabsContent>
      <TabsContent value="projects" className="mt-3">
        <p className="text-sm text-muted-foreground">
          Projects — a list of your active and archived projects.
        </p>
      </TabsContent>
      <TabsContent value="packages" className="mt-3">
        <p className="text-sm text-muted-foreground">
          Packages — published packages and their version history.
        </p>
      </TabsContent>
      <TabsContent value="new" className="mt-3">
        <p className="text-sm text-muted-foreground">
          New — create a new project, package, or resource.
        </p>
      </TabsContent>
      <TabsContent value="insights" className="mt-3">
        <p className="text-sm text-muted-foreground">
          Insights — repository activity, contributors, and pulse data.
        </p>
      </TabsContent>
      <TabsContent value="settings" className="mt-3">
        <p className="text-sm text-muted-foreground">
          Settings — manage repository options, webhooks, and access
          control.
        </p>
      </TabsContent>
    </Tabs>
  )
}

// ─── 12. Ghost ────────────────────────────────────────────────────────────────
export function GhostVariant() {
  return (
    <Tabs defaultValue="overview">
      <TabsList className="h-auto gap-1 rounded-none bg-transparent p-0">
        <TabsTrigger
          value="overview"
          className="rounded-md bg-transparent px-3 py-1.5 text-sm text-muted-foreground hover:bg-muted hover:text-foreground data-active:bg-transparent data-active:font-medium data-active:text-foreground data-active:shadow-none"
        >
          Overview
        </TabsTrigger>
        <TabsTrigger
          value="analytics"
          className="rounded-md bg-transparent px-3 py-1.5 text-sm text-muted-foreground hover:bg-muted hover:text-foreground data-active:bg-transparent data-active:font-medium data-active:text-foreground data-active:shadow-none"
        >
          Analytics
        </TabsTrigger>
        <TabsTrigger
          value="reports"
          className="rounded-md bg-transparent px-3 py-1.5 text-sm text-muted-foreground hover:bg-muted hover:text-foreground data-active:bg-transparent data-active:font-medium data-active:text-foreground data-active:shadow-none"
        >
          Reports
        </TabsTrigger>
      </TabsList>
      <TabsContent value="overview" className="mt-3">
        <p className="text-sm text-muted-foreground">
          Ghost tabs have no background fill — the active tab is indicated
          only by text weight and color.
        </p>
      </TabsContent>
      <TabsContent value="analytics" className="mt-3">
        <p className="text-sm text-muted-foreground">
          Analytics panel in ghost mode — minimal visual noise lets your
          content shine.
        </p>
      </TabsContent>
      <TabsContent value="reports" className="mt-3">
        <p className="text-sm text-muted-foreground">
          Reports in ghost style — ideal for secondary navigation within a
          card or sidebar.
        </p>
      </TabsContent>
    </Tabs>
  )
}

// ─── 13. Settings ─────────────────────────────────────────────────────────────
export function SettingsVariant() {
  return (
    <Tabs defaultValue="account">
      <TabsList>
        <TabsTrigger value="account">Account</TabsTrigger>
        <TabsTrigger value="password">Password</TabsTrigger>
        <TabsTrigger value="notifications">Notifications</TabsTrigger>
      </TabsList>
      <TabsContent value="account" className="mt-4 space-y-4">
        <div className="space-y-2">
          <Label htmlFor="settings-name">Display name</Label>
          <Input id="settings-name" placeholder="Your name" defaultValue="Jane Doe" />
        </div>
        <div className="space-y-2">
          <Label htmlFor="settings-email">Email</Label>
          <Input id="settings-email" type="email" placeholder="Email" defaultValue="jane@example.com" />
        </div>
        <Button size="sm">Save account</Button>
      </TabsContent>
      <TabsContent value="password" className="mt-4 space-y-4">
        <div className="space-y-2">
          <Label htmlFor="settings-current-pw">Current password</Label>
          <Input id="settings-current-pw" type="password" placeholder="••••••••" />
        </div>
        <div className="space-y-2">
          <Label htmlFor="settings-new-pw">New password</Label>
          <Input id="settings-new-pw" type="password" placeholder="••••••••" />
        </div>
        <Button size="sm">Update password</Button>
      </TabsContent>
      <TabsContent value="notifications" className="mt-4 space-y-4">
        <div className="space-y-2">
          <Label htmlFor="settings-notif-email">Notification email</Label>
          <Input id="settings-notif-email" type="email" placeholder="alerts@example.com" />
        </div>
        <Button size="sm">Save preferences</Button>
      </TabsContent>
    </Tabs>
  )
}

// ─── 14. Icon only ────────────────────────────────────────────────────────────
export function IconOnlyVariant() {
  return (
    <Tabs defaultValue="home">
      <TabsList>
        <TabsTrigger value="home" aria-label="Home">
          <Home />
        </TabsTrigger>
        <TabsTrigger value="folder" aria-label="Folder">
          <Folder />
        </TabsTrigger>
        <TabsTrigger value="package" aria-label="Package">
          <Package />
        </TabsTrigger>
        <TabsTrigger value="settings" aria-label="Settings">
          <Settings />
        </TabsTrigger>
      </TabsList>
      <TabsContent value="home" className="mt-3">
        <p className="text-sm text-muted-foreground">
          Home — the main landing panel for your workspace.
        </p>
      </TabsContent>
      <TabsContent value="folder" className="mt-3">
        <p className="text-sm text-muted-foreground">
          Folder — browse your files and project directories.
        </p>
      </TabsContent>
      <TabsContent value="package" className="mt-3">
        <p className="text-sm text-muted-foreground">
          Package — view and manage installed dependencies.
        </p>
      </TabsContent>
      <TabsContent value="settings" className="mt-3">
        <p className="text-sm text-muted-foreground">
          Settings — configure preferences and workspace options.
        </p>
      </TabsContent>
    </Tabs>
  )
}

// ─── 15. Rich ─────────────────────────────────────────────────────────────────
const RICH_METRICS = [
  {
    value: "revenue",
    label: "Revenue",
    metric: "$24,512",
    change: "+12.5%",
    positive: true,
    items: ["Enterprise plan — $18,200", "Pro plan — $4,800", "Starter — $1,512"],
  },
  {
    value: "users",
    label: "Users",
    metric: "8,340",
    change: "+7.2%",
    positive: true,
    items: ["New signups — 1,240", "Active users — 6,800", "Churned — 300"],
  },
  {
    value: "performance",
    label: "Performance",
    metric: "98.7%",
    change: "-0.3%",
    positive: false,
    items: ["Uptime SLA — 99.9%", "Avg latency — 42 ms", "Error rate — 0.1%"],
  },
]

export function RichVariant() {
  return (
    <Tabs defaultValue="revenue">
      <TabsList>
        {RICH_METRICS.map((m) => (
          <TabsTrigger key={m.value} value={m.value}>
            {m.label}
          </TabsTrigger>
        ))}
      </TabsList>
      {RICH_METRICS.map((m) => (
        <TabsContent key={m.value} value={m.value} className="mt-3">
          <Card className="p-4">
            <div className="flex items-baseline gap-3">
              <span className="text-2xl font-bold">{m.metric}</span>
              <span
                className={cn(
                  "text-xs font-medium",
                  m.positive ? "text-emerald-600" : "text-rose-500"
                )}
              >
                {m.change} vs last month
              </span>
            </div>
            <ul className="mt-3 space-y-1">
              {m.items.map((item) => (
                <li key={item} className="flex items-center gap-2 text-xs text-muted-foreground">
                  <span className="size-1.5 shrink-0 rounded-full bg-muted-foreground/50" />
                  {item}
                </li>
              ))}
            </ul>
          </Card>
        </TabsContent>
      ))}
    </Tabs>
  )
}
