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
  WithIconVariant,
  WithTextIconVariant,
  OutlineVariant,
  SizesVariant,
  DisabledVariant,
  ToggleGroupVariant,
} from "@/components/pages/components-toggle/variants"

const VARIANTS: {
  title: string
  description: string
  component: React.FC
}[] = [
  {
    title: "Basic",
    description: "A single toggle button that switches between pressed and unpressed states.",
    component: BasicVariant,
  },
  {
    title: "With icon",
    description: "An icon-only toggle button with an accessible aria-label.",
    component: WithIconVariant,
  },
  {
    title: "With text + icon",
    description: "A toggle that pairs an icon with a text label inside the button.",
    component: WithTextIconVariant,
  },
  {
    title: "Outline",
    description: "Outline-variant toggles with bordered styling for Star, Heart, and Bookmark.",
    component: OutlineVariant,
  },
  {
    title: "Sizes",
    description: "Small, default, and large toggle sizes displayed side-by-side.",
    component: SizesVariant,
  },
  {
    title: "Disabled",
    description: "Disabled unpressed and disabled pressed toggle states.",
    component: DisabledVariant,
  },
  {
    title: "Toggle group",
    description: "A multi-select formatting toolbar (Bold/Italic/Underline) and a single-select alignment group.",
    component: ToggleGroupVariant,
  },
]

export default function TogglePage() {
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
            <BreadcrumbPage>Toggle</BreadcrumbPage>
          </BreadcrumbItem>
        </BreadcrumbList>
      </Breadcrumb>

      {/* Header */}
      <div className="flex flex-col gap-2">
        <h1 className="text-2xl font-semibold tracking-tight">Toggle</h1>
        <p className="text-sm text-muted-foreground">
          A two-state button that can be either on or off. Browse our collection of {VARIANTS.length} Toggle variants.
        </p>
      </div>

      {/* Grid */}
      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
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
