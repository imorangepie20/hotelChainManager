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
  WithLabelVariant,
  RequiredVariant,
  HelperTextVariant,
  ErrorStateVariant,
  CharacterCounterVariant,
  AutoGrowVariant,
  WithButtonVariant,
  DisabledVariant,
  AvatarCommentVariant,
  ResizableFixedVariant,
} from "@/components/pages/components-textarea/variants"

const VARIANTS: {
  title: string
  description: string
  component: React.FC
}[] = [
  {
    title: "Basic",
    description: "A plain textarea with a placeholder.",
    component: BasicVariant,
  },
  {
    title: "With label",
    description: "A textarea paired with an accessible label.",
    component: WithLabelVariant,
  },
  {
    title: "Required",
    description: "A textarea with a required label asterisk and helper hint.",
    component: RequiredVariant,
  },
  {
    title: "Helper text",
    description: "A textarea with a muted helper text description below.",
    component: HelperTextVariant,
  },
  {
    title: "Error state",
    description: "A textarea in an error state with a destructive helper message.",
    component: ErrorStateVariant,
  },
  {
    title: "Character counter",
    description: "A textarea that shows a live character count against a 200-char limit.",
    component: CharacterCounterVariant,
  },
  {
    title: "Auto-grow",
    description: "A textarea that grows in height as you add new lines.",
    component: AutoGrowVariant,
  },
  {
    title: "With button",
    description: "A textarea with a Send button — a simple comment composer.",
    component: WithButtonVariant,
  },
  {
    title: "Disabled",
    description: "A textarea in a disabled state — not focusable or editable.",
    component: DisabledVariant,
  },
  {
    title: "Avatar comment",
    description: "An avatar next to a textarea and Post button — a comment box layout.",
    component: AvatarCommentVariant,
  },
  {
    title: "Resizable / fixed",
    description: "Two textareas: one fixed (resize-none) and one vertically resizable.",
    component: ResizableFixedVariant,
  },
]

export default function TextareaPage() {
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
            <BreadcrumbPage>Textarea</BreadcrumbPage>
          </BreadcrumbItem>
        </BreadcrumbList>
      </Breadcrumb>

      {/* Header */}
      <div className="flex flex-col gap-2">
        <h1 className="text-2xl font-semibold tracking-tight">Textarea</h1>
        <p className="text-sm text-muted-foreground">
          Displays a form textarea or a component that looks like a textarea.
          Browse our collection of {VARIANTS.length} Textarea variants.
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
