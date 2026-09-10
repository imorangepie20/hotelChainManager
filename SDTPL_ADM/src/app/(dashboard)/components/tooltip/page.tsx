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
  PositionsVariant,
  WithTitleVariant,
  StatsVariant,
  IconButtonsVariant,
  WithShortcutVariant,
  RichPreviewVariant,
  NoArrowVariant,
} from "@/components/pages/components-tooltip/variants"

const VARIANTS: {
  title: string
  description: string
  component: React.FC
}[] = [
  {
    title: "Basic",
    description: "A simple outline button that shows a tooltip on hover.",
    component: BasicVariant,
  },
  {
    title: "Positions",
    description:
      "Four buttons demonstrating top, right, bottom, and left tooltip placement.",
    component: PositionsVariant,
  },
  {
    title: "With title",
    description:
      "A tooltip with a bold title line and a supporting description.",
    component: WithTitleVariant,
  },
  {
    title: "Stats",
    description:
      "An info icon button that reveals a stats summary in the tooltip.",
    component: StatsVariant,
  },
  {
    title: "Icon buttons",
    description:
      "A text-formatting toolbar where each icon button has a descriptive tooltip.",
    component: IconButtonsVariant,
  },
  {
    title: "With shortcut",
    description:
      "A Save button whose tooltip includes a keyboard shortcut badge.",
    component: WithShortcutVariant,
  },
  {
    title: "Rich preview",
    description:
      "A wider tooltip with a gradient image block and product details.",
    component: RichPreviewVariant,
  },
  {
    title: "No arrow",
    description: "A plain tooltip with the arrow indicator hidden.",
    component: NoArrowVariant,
  },
]

export default function TooltipPage() {
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
            <BreadcrumbPage>Tooltip</BreadcrumbPage>
          </BreadcrumbItem>
        </BreadcrumbList>
      </Breadcrumb>

      {/* Header */}
      <div className="flex flex-col gap-2">
        <h1 className="text-2xl font-semibold tracking-tight">Tooltip</h1>
        <p className="text-sm text-muted-foreground">
          A popup that displays information related to an element when the
          element receives keyboard focus or the mouse hovers over it. Browse
          our collection of {VARIANTS.length} Tooltip variants.
        </p>
      </div>

      {/* Grid */}
      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
        {VARIANTS.map(({ title, description, component: Component }) => (
          <Card key={title} className="min-h-56">
            <CardHeader>
              <CardTitle>{title}</CardTitle>
              <p className="text-sm text-muted-foreground">{description}</p>
            </CardHeader>
            <CardContent className="flex flex-col gap-3 py-4">
              <div className="flex min-h-10 items-center justify-center overflow-visible py-4">
                <Component />
              </div>
              <p className="text-xs text-muted-foreground">
                Hover the trigger
              </p>
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  )
}
