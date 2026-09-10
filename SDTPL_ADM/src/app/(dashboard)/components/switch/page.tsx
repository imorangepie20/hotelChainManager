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
  AirplaneModeVariant,
  LabelWithSublabelVariant,
  DisabledVariant,
  SizesVariant,
  ColoredVariant,
  WithIconsVariant,
  SquareVariant,
  SettingsListVariant,
  CardVariant,
} from "@/components/pages/components-switch/variants"

const VARIANTS: {
  title: string
  description: string
  component: React.FC
}[] = [
  {
    title: "Basic",
    description: "A single switch in its default off state.",
    component: BasicVariant,
  },
  {
    title: "Airplane mode",
    description: "A switch paired with a label that toggles the switch on click.",
    component: AirplaneModeVariant,
  },
  {
    title: "Label with sublabel",
    description: "A switch with a primary label and a muted sub-description on the left.",
    component: LabelWithSublabelVariant,
  },
  {
    title: "Disabled",
    description: "Disabled-off and disabled-on switches shown side by side.",
    component: DisabledVariant,
  },
  {
    title: "Sizes",
    description: "Small, default, and large switch sizes in a single row.",
    component: SizesVariant,
  },
  {
    title: "Colored",
    description: "Custom checked colors — emerald, blue, and rose — applied via className.",
    component: ColoredVariant,
  },
  {
    title: "With icons",
    description: "A theme-style switch showing a Sun icon when off and a Moon icon when on.",
    component: WithIconsVariant,
  },
  {
    title: "Square",
    description: "A switch with square corners on both the track and the thumb.",
    component: SquareVariant,
  },
  {
    title: "Settings list",
    description: "A list of setting rows each with a label, description, and switch separated by dividers.",
    component: SettingsListVariant,
  },
  {
    title: "Card",
    description: "A bordered card that highlights its ring when the switch is turned on.",
    component: CardVariant,
  },
]

export default function SwitchPage() {
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
            <BreadcrumbPage>Switch</BreadcrumbPage>
          </BreadcrumbItem>
        </BreadcrumbList>
      </Breadcrumb>

      {/* Header */}
      <div className="flex flex-col gap-2">
        <h1 className="text-2xl font-semibold tracking-tight">Switch</h1>
        <p className="text-sm text-muted-foreground">
          A control that allows the user to toggle between checked and not checked. Browse our
          collection of {VARIANTS.length} Switch variants.
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
