import Link from "next/link"

import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from "@/components/ui/breadcrumb"
import {
  BasicVariant,
  WithCardVariant,
  StripedVariant,
  HoverableVariant,
  BorderlessVariant,
  WithCaptionVariant,
  WithFooterVariant,
  CompactVariant,
  RichVariant,
  WithActionsVariant,
  SelectableVariant,
  StickyHeaderVariant,
} from "@/components/pages/components-table/variants"

const VARIANTS: {
  title: string
  description: string
  component: React.FC
}[] = [
  {
    title: "Basic",
    description: "A simple table with header, body rows, and right-aligned currency amounts.",
    component: BasicVariant,
  },
  {
    title: "With card",
    description: "The table wrapped in a Card with a 'Recent payments' header.",
    component: WithCardVariant,
  },
  {
    title: "Striped",
    description: "Alternating row backgrounds using nth-child to improve scannability.",
    component: StripedVariant,
  },
  {
    title: "Hoverable",
    description: "Rows highlight on hover — leverages the default TableRow hover style.",
    component: HoverableVariant,
  },
  {
    title: "Borderless",
    description: "All row borders removed for a cleaner, spacious look.",
    component: BorderlessVariant,
  },
  {
    title: "With caption",
    description: "A TableCaption below the table provides context for screen readers.",
    component: WithCaptionVariant,
  },
  {
    title: "With footer (totals)",
    description: "A TableFooter row displays the summed total of all amounts.",
    component: WithFooterVariant,
  },
  {
    title: "Compact",
    description: "Reduced row padding and smaller text for dense data display.",
    component: CompactVariant,
  },
  {
    title: "Rich (avatars + badges)",
    description: "Customer cell shows an Avatar with initials, name, and email.",
    component: RichVariant,
  },
  {
    title: "With actions",
    description: "An extra Actions column with a ⋯ dropdown per row (View / Edit / Delete).",
    component: WithActionsVariant,
  },
  {
    title: "Selectable rows",
    description: "Leading checkbox column with header select-all (incl. indeterminate state) and a live selection counter.",
    component: SelectableVariant,
  },
  {
    title: "Sticky header",
    description: "Table inside a fixed-height scrollable container; the header stays pinned at the top.",
    component: StickyHeaderVariant,
  },
]

export default function TablePage() {
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
            <BreadcrumbPage>Table</BreadcrumbPage>
          </BreadcrumbItem>
        </BreadcrumbList>
      </Breadcrumb>

      {/* Header */}
      <div className="flex flex-col gap-2">
        <h1 className="text-2xl font-semibold tracking-tight">Table</h1>
        <p className="text-sm text-muted-foreground">
          A responsive table component for displaying tabular data. Browse our collection of{" "}
          {VARIANTS.length} Table variants.
        </p>
      </div>

      {/* Variant sections — stacked (tables are wide) */}
      <div className="flex flex-col gap-8">
        {VARIANTS.map(({ title, description, component: Component }) => (
          <section key={title} className="flex flex-col gap-3">
            <div>
              <h2 className="text-base font-semibold">{title}</h2>
              <p className="text-sm text-muted-foreground">{description}</p>
            </div>
            <Component />
          </section>
        ))}
      </div>
    </div>
  )
}
