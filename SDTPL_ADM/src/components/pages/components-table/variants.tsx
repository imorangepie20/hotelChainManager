"use client"

import * as React from "react"
import { MoreHorizontal, Pencil, Trash2 } from "lucide-react"

import {
  Table,
  TableHeader,
  TableBody,
  TableFooter,
  TableHead,
  TableRow,
  TableCell,
  TableCaption,
} from "@/components/ui/table"
import { Badge } from "@/components/ui/badge"
import { Avatar, AvatarFallback } from "@/components/ui/avatar"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Checkbox } from "@/components/ui/checkbox"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { cn } from "@/lib/utils"

// ─── Shared dataset ────────────────────────────────────────────────────────────
const payments = [
  { invoice: "INV001", customer: "Olivia Martin",  email: "olivia@example.com",  method: "Credit Card",   status: "Paid",    amount: 316.00 },
  { invoice: "INV002", customer: "Jackson Lee",    email: "jackson@example.com", method: "PayPal",        status: "Pending", amount: 242.00 },
  { invoice: "INV003", customer: "Isabella Nguyen",email: "isabella@example.com",method: "Bank Transfer", status: "Unpaid",  amount: 837.00 },
  { invoice: "INV004", customer: "William Kim",    email: "william@example.com", method: "Credit Card",   status: "Paid",    amount: 874.00 },
  { invoice: "INV005", customer: "Sofia Davis",    email: "sofia@example.com",   method: "PayPal",        status: "Pending", amount: 721.00 },
]

const fmt = new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" })

function statusBadge(status: string) {
  if (status === "Paid")
    return <Badge className="bg-emerald-500/15 text-emerald-700 dark:text-emerald-400 border-0">{status}</Badge>
  if (status === "Pending")
    return <Badge className="bg-amber-500/15 text-amber-700 dark:text-amber-400 border-0">{status}</Badge>
  return <Badge className="bg-rose-500/15 text-rose-700 dark:text-rose-400 border-0">{status}</Badge>
}

function initials(name: string) {
  return name.split(" ").map((n) => n[0]).join("").slice(0, 2).toUpperCase()
}

// ─── 1. Basic ─────────────────────────────────────────────────────────────────
export function BasicVariant() {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Invoice</TableHead>
          <TableHead>Customer</TableHead>
          <TableHead>Method</TableHead>
          <TableHead>Status</TableHead>
          <TableHead className="text-right">Amount</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {payments.map((p) => (
          <TableRow key={p.invoice}>
            <TableCell className="font-medium">{p.invoice}</TableCell>
            <TableCell>{p.customer}</TableCell>
            <TableCell>{p.method}</TableCell>
            <TableCell>{statusBadge(p.status)}</TableCell>
            <TableCell className="text-right">{fmt.format(p.amount)}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}

// ─── 2. With card ─────────────────────────────────────────────────────────────
export function WithCardVariant() {
  return (
    <Card>
      <CardHeader>
        <CardTitle>Recent payments</CardTitle>
      </CardHeader>
      <CardContent className="p-0">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Invoice</TableHead>
              <TableHead>Customer</TableHead>
              <TableHead>Method</TableHead>
              <TableHead>Status</TableHead>
              <TableHead className="text-right">Amount</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {payments.map((p) => (
              <TableRow key={p.invoice}>
                <TableCell className="font-medium">{p.invoice}</TableCell>
                <TableCell>{p.customer}</TableCell>
                <TableCell>{p.method}</TableCell>
                <TableCell>{statusBadge(p.status)}</TableCell>
                <TableCell className="text-right">{fmt.format(p.amount)}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  )
}

// ─── 3. Striped ───────────────────────────────────────────────────────────────
export function StripedVariant() {
  return (
    <Table className="[&_tbody_tr:nth-child(even)]:bg-muted/50">
      <TableHeader>
        <TableRow>
          <TableHead>Invoice</TableHead>
          <TableHead>Customer</TableHead>
          <TableHead>Method</TableHead>
          <TableHead>Status</TableHead>
          <TableHead className="text-right">Amount</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {payments.map((p) => (
          <TableRow key={p.invoice}>
            <TableCell className="font-medium">{p.invoice}</TableCell>
            <TableCell>{p.customer}</TableCell>
            <TableCell>{p.method}</TableCell>
            <TableCell>{statusBadge(p.status)}</TableCell>
            <TableCell className="text-right">{fmt.format(p.amount)}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}

// ─── 4. Hoverable ─────────────────────────────────────────────────────────────
export function HoverableVariant() {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Invoice</TableHead>
          <TableHead>Customer</TableHead>
          <TableHead>Method</TableHead>
          <TableHead>Status</TableHead>
          <TableHead className="text-right">Amount</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {payments.map((p) => (
          // TableRow already applies hover:bg-muted/50 by default
          <TableRow key={p.invoice} className="cursor-pointer">
            <TableCell className="font-medium">{p.invoice}</TableCell>
            <TableCell>{p.customer}</TableCell>
            <TableCell>{p.method}</TableCell>
            <TableCell>{statusBadge(p.status)}</TableCell>
            <TableCell className="text-right">{fmt.format(p.amount)}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}

// ─── 5. Borderless ────────────────────────────────────────────────────────────
export function BorderlessVariant() {
  return (
    <Table className="[&_tr]:border-0">
      <TableHeader>
        <TableRow>
          <TableHead>Invoice</TableHead>
          <TableHead>Customer</TableHead>
          <TableHead>Method</TableHead>
          <TableHead>Status</TableHead>
          <TableHead className="text-right">Amount</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {payments.map((p) => (
          <TableRow key={p.invoice}>
            <TableCell className="font-medium">{p.invoice}</TableCell>
            <TableCell>{p.customer}</TableCell>
            <TableCell>{p.method}</TableCell>
            <TableCell>{statusBadge(p.status)}</TableCell>
            <TableCell className="text-right">{fmt.format(p.amount)}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}

// ─── 6. With caption ──────────────────────────────────────────────────────────
export function WithCaptionVariant() {
  return (
    <Table>
      <TableCaption>A list of your recent invoices.</TableCaption>
      <TableHeader>
        <TableRow>
          <TableHead>Invoice</TableHead>
          <TableHead>Customer</TableHead>
          <TableHead>Method</TableHead>
          <TableHead>Status</TableHead>
          <TableHead className="text-right">Amount</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {payments.map((p) => (
          <TableRow key={p.invoice}>
            <TableCell className="font-medium">{p.invoice}</TableCell>
            <TableCell>{p.customer}</TableCell>
            <TableCell>{p.method}</TableCell>
            <TableCell>{statusBadge(p.status)}</TableCell>
            <TableCell className="text-right">{fmt.format(p.amount)}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}

// ─── 7. With footer (totals) ──────────────────────────────────────────────────
export function WithFooterVariant() {
  const total = payments.reduce((sum, p) => sum + p.amount, 0)
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Invoice</TableHead>
          <TableHead>Customer</TableHead>
          <TableHead>Method</TableHead>
          <TableHead>Status</TableHead>
          <TableHead className="text-right">Amount</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {payments.map((p) => (
          <TableRow key={p.invoice}>
            <TableCell className="font-medium">{p.invoice}</TableCell>
            <TableCell>{p.customer}</TableCell>
            <TableCell>{p.method}</TableCell>
            <TableCell>{statusBadge(p.status)}</TableCell>
            <TableCell className="text-right">{fmt.format(p.amount)}</TableCell>
          </TableRow>
        ))}
      </TableBody>
      <TableFooter>
        <TableRow>
          <TableCell colSpan={4}>Total</TableCell>
          <TableCell className="text-right">{fmt.format(total)}</TableCell>
        </TableRow>
      </TableFooter>
    </Table>
  )
}

// ─── 8. Compact ───────────────────────────────────────────────────────────────
export function CompactVariant() {
  return (
    <Table className="[&_td]:py-1.5 [&_th]:py-1.5 text-sm">
      <TableHeader>
        <TableRow>
          <TableHead>Invoice</TableHead>
          <TableHead>Customer</TableHead>
          <TableHead>Method</TableHead>
          <TableHead>Status</TableHead>
          <TableHead className="text-right">Amount</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {payments.map((p) => (
          <TableRow key={p.invoice}>
            <TableCell className="font-medium">{p.invoice}</TableCell>
            <TableCell>{p.customer}</TableCell>
            <TableCell>{p.method}</TableCell>
            <TableCell>{statusBadge(p.status)}</TableCell>
            <TableCell className="text-right">{fmt.format(p.amount)}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}

// ─── 9. Rich (avatars + badges) ───────────────────────────────────────────────
export function RichVariant() {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Invoice</TableHead>
          <TableHead>Customer</TableHead>
          <TableHead>Method</TableHead>
          <TableHead>Status</TableHead>
          <TableHead className="text-right">Amount</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {payments.map((p) => (
          <TableRow key={p.invoice}>
            <TableCell className="font-medium">{p.invoice}</TableCell>
            <TableCell>
              <div className="flex items-center gap-2">
                <Avatar size="sm">
                  <AvatarFallback>{initials(p.customer)}</AvatarFallback>
                </Avatar>
                <div className="flex flex-col">
                  <span className="text-sm font-medium leading-tight">{p.customer}</span>
                  <span className="text-xs text-muted-foreground">{p.email}</span>
                </div>
              </div>
            </TableCell>
            <TableCell>{p.method}</TableCell>
            <TableCell>{statusBadge(p.status)}</TableCell>
            <TableCell className="text-right">{fmt.format(p.amount)}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}

// ─── 10. With actions ─────────────────────────────────────────────────────────
export function WithActionsVariant() {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Invoice</TableHead>
          <TableHead>Customer</TableHead>
          <TableHead>Method</TableHead>
          <TableHead>Status</TableHead>
          <TableHead className="text-right">Amount</TableHead>
          <TableHead />
        </TableRow>
      </TableHeader>
      <TableBody>
        {payments.map((p) => (
          <TableRow key={p.invoice}>
            <TableCell className="font-medium">{p.invoice}</TableCell>
            <TableCell>{p.customer}</TableCell>
            <TableCell>{p.method}</TableCell>
            <TableCell>{statusBadge(p.status)}</TableCell>
            <TableCell className="text-right">{fmt.format(p.amount)}</TableCell>
            <TableCell>
              <DropdownMenu>
                <DropdownMenuTrigger
                  render={
                    <Button variant="ghost" size="icon" className="size-8">
                      <MoreHorizontal className="size-4" />
                      <span className="sr-only">Open menu</span>
                    </Button>
                  }
                />
                <DropdownMenuContent align="end">
                  <DropdownMenuLabel>Actions</DropdownMenuLabel>
                  <DropdownMenuSeparator />
                  <DropdownMenuItem>
                    View
                  </DropdownMenuItem>
                  <DropdownMenuItem>
                    <Pencil className="size-4" />
                    Edit
                  </DropdownMenuItem>
                  <DropdownMenuItem variant="destructive">
                    <Trash2 className="size-4" />
                    Delete
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}

// ─── 11. Selectable rows ──────────────────────────────────────────────────────
export function SelectableVariant() {
  const [selected, setSelected] = React.useState<Set<string>>(new Set())

  const allSelected = selected.size === payments.length
  const someSelected = selected.size > 0 && !allSelected

  function toggleAll() {
    if (allSelected) {
      setSelected(new Set())
    } else {
      setSelected(new Set(payments.map((p) => p.invoice)))
    }
  }

  function toggleRow(invoice: string) {
    setSelected((prev) => {
      const next = new Set(prev)
      if (next.has(invoice)) next.delete(invoice)
      else next.add(invoice)
      return next
    })
  }

  return (
    <div data-testid="table-selectable" className="flex flex-col gap-2">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead className="w-10">
              <Checkbox
                checked={allSelected}
                indeterminate={someSelected}
                onCheckedChange={toggleAll}
                aria-label="Select all rows"
              />
            </TableHead>
            <TableHead>Invoice</TableHead>
            <TableHead>Customer</TableHead>
            <TableHead>Method</TableHead>
            <TableHead>Status</TableHead>
            <TableHead className="text-right">Amount</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {payments.map((p) => {
            const isSelected = selected.has(p.invoice)
            return (
              <TableRow
                key={p.invoice}
                className={cn(isSelected && "bg-muted/50")}
                data-state={isSelected ? "selected" : undefined}
              >
                <TableCell>
                  <Checkbox
                    checked={isSelected}
                    onCheckedChange={() => toggleRow(p.invoice)}
                    aria-label={`Select ${p.invoice}`}
                  />
                </TableCell>
                <TableCell className="font-medium">{p.invoice}</TableCell>
                <TableCell>{p.customer}</TableCell>
                <TableCell>{p.method}</TableCell>
                <TableCell>{statusBadge(p.status)}</TableCell>
                <TableCell className="text-right">{fmt.format(p.amount)}</TableCell>
              </TableRow>
            )
          })}
        </TableBody>
      </Table>
      <p className="text-sm text-muted-foreground">
        {selected.size} of {payments.length} row(s) selected.
      </p>
    </div>
  )
}

// ─── 12. Sticky header ────────────────────────────────────────────────────────
const extendedPayments = [
  ...payments,
  { invoice: "INV006", customer: "Liam Johnson",   email: "liam@example.com",   method: "Credit Card",   status: "Paid",    amount: 512.00 },
  { invoice: "INV007", customer: "Emma Wilson",    email: "emma@example.com",   method: "PayPal",        status: "Unpaid",  amount: 345.00 },
  { invoice: "INV008", customer: "Noah Brown",     email: "noah@example.com",   method: "Bank Transfer", status: "Pending", amount: 678.00 },
  { invoice: "INV009", customer: "Ava Jones",      email: "ava@example.com",    method: "Credit Card",   status: "Paid",    amount: 199.00 },
]

export function StickyHeaderVariant() {
  return (
    <div className="max-h-64 overflow-auto rounded-md border [&_thead]:sticky [&_thead]:top-0 [&_thead]:bg-background [&_thead]:z-10">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Invoice</TableHead>
            <TableHead>Customer</TableHead>
            <TableHead>Method</TableHead>
            <TableHead>Status</TableHead>
            <TableHead className="text-right">Amount</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {extendedPayments.map((p) => (
            <TableRow key={p.invoice}>
              <TableCell className="font-medium">{p.invoice}</TableCell>
              <TableCell>{p.customer}</TableCell>
              <TableCell>{p.method}</TableCell>
              <TableCell>{statusBadge(p.status)}</TableCell>
              <TableCell className="text-right">{fmt.format(p.amount)}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  )
}
