import Link from "next/link"

import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from "@/components/ui/breadcrumb"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import {
  BasicVariant,
  UnderlineVariant,
  PillsVariant,
  VerticalVariant,
  WithIconsVariant,
  FullWidthVariant,
  BoxedVariant,
  DisabledTabVariant,
  WithBadgesVariant,
  SegmentedVariant,
  ScrollableVariant,
  GhostVariant,
  SettingsVariant,
  IconOnlyVariant,
  RichVariant,
} from "@/components/pages/components-tabs/variants"

const VARIANTS: {
  title: string
  description: string
  component: React.FC
}[] = [
  {
    title: "Basic",
    description: "The default segmented tabs look with Overview, Analytics, and Reports panels.",
    component: BasicVariant,
  },
  {
    title: "Underline",
    description: "Active tab indicated by a bottom-border underline with no filled background.",
    component: UnderlineVariant,
  },
  {
    title: "Pills",
    description: "Rounded-full pill triggers with a filled primary background on the active item.",
    component: PillsVariant,
  },
  {
    title: "Vertical",
    description: "Tab list stacked in a left-side column with the content panel beside it.",
    component: VerticalVariant,
  },
  {
    title: "With icons",
    description: "Triggers with a leading icon alongside the label for quick visual scanning.",
    component: WithIconsVariant,
  },
  {
    title: "Full width",
    description: "Tabs stretch equally to fill the full container width — great for mobile layouts.",
    component: FullWidthVariant,
  },
  {
    title: "Boxed",
    description: "Card-style tab triggers that appear connected to the bordered content panel below.",
    component: BoxedVariant,
  },
  {
    title: "Disabled tab",
    description: "Middle tab is disabled — it cannot be clicked or focused.",
    component: DisabledTabVariant,
  },
  {
    title: "With badges",
    description: "Count badges on each trigger surface unread or pending items at a glance.",
    component: WithBadgesVariant,
  },
  {
    title: "Segmented",
    description: "A compact segmented control with rounded-full triggers for time-range selection.",
    component: SegmentedVariant,
  },
  {
    title: "Scrollable",
    description: "Six triggers in a horizontally scrollable list — handles many tabs gracefully.",
    component: ScrollableVariant,
  },
  {
    title: "Ghost",
    description: "Minimal triggers with no background — active state shown only through text weight.",
    component: GhostVariant,
  },
  {
    title: "Settings",
    description: "Account, Password, and Notifications panels each containing a small form.",
    component: SettingsVariant,
  },
  {
    title: "Icon only",
    description: "Compact icon-only triggers with aria-labels for accessible icon navigation.",
    component: IconOnlyVariant,
  },
  {
    title: "Rich",
    description: "Panels contain rich content cards with a headline metric, trend badge, and item list.",
    component: RichVariant,
  },
]

export default function TabsPage() {
  return (
    <div className="flex flex-col gap-8 p-6 pb-12">
      {/* Breadcrumb */}
      <Breadcrumb>
        <BreadcrumbList>
          <BreadcrumbItem>
            <BreadcrumbLink render={<Link href="/" />}>Home</BreadcrumbLink>
          </BreadcrumbItem>
          <BreadcrumbSeparator />
          <BreadcrumbItem>
            <BreadcrumbLink render={<Link href="/components" />}>
              Components
            </BreadcrumbLink>
          </BreadcrumbItem>
          <BreadcrumbSeparator />
          <BreadcrumbItem>
            <BreadcrumbPage>Tabs</BreadcrumbPage>
          </BreadcrumbItem>
        </BreadcrumbList>
      </Breadcrumb>

      {/* Header */}
      <div className="flex flex-col gap-2">
        <h1 className="text-2xl font-semibold tracking-tight">Tabs</h1>
        <p className="text-sm text-muted-foreground">
          A set of layered sections of content—known as tab panels—displayed one at a time. Browse
          our collection of {VARIANTS.length} Tabs variants.
        </p>
      </div>

      {/* Grid */}
      <div className="grid gap-4 md:grid-cols-2">
        {VARIANTS.map(({ title, description, component: Component }) => (
          <Card key={title}>
            <CardHeader>
              <CardTitle>{title}</CardTitle>
              <p className="text-sm text-muted-foreground">{description}</p>
            </CardHeader>
            <CardContent className="py-4">
              <Component />
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  )
}
