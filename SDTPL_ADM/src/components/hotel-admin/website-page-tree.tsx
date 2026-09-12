"use client";

import { useState } from "react";
import { ChevronRight, FileText, FolderTree, Hotel } from "lucide-react";

import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import type { WebsitePageTreeItem } from "@/lib/staff-api";
import { cn } from "@/lib/utils";

type WebsitePageTreeProps = {
  pages: readonly WebsitePageTreeItem[];
  selectedPageId: string | null;
  onSelectPage: (pageId: string) => void;
  className?: string;
};

function pageIcon(page: WebsitePageTreeItem) {
  return page.pageType === "HOTEL_LANDING"
    ? <Hotel className="size-3.5 shrink-0 text-muted-foreground" aria-hidden="true" />
    : <FileText className="size-3.5 shrink-0 text-muted-foreground" aria-hidden="true" />;
}

function statusText(page: WebsitePageTreeItem) {
  if (page.lifecycleStatus === "ARCHIVED") return "보관";
  return page.status === "PUBLISHED" ? "발행" : page.status === "CHANGED_AFTER_PUBLISH" ? "초안 변경" : "초안";
}

function PageLeaf({ page, selectedPageId, onSelectPage }: Pick<WebsitePageTreeProps, "selectedPageId" | "onSelectPage"> & { page: WebsitePageTreeItem }) {
  const selected = page.id === selectedPageId;
  return <button type="button" aria-current={selected ? "page" : undefined} onClick={() => onSelectPage(page.id)} className={cn(
    "flex min-h-9 w-full items-center gap-2 rounded-lg px-2 text-left text-sm outline-none transition-colors focus-visible:ring-2 focus-visible:ring-ring",
    selected ? "bg-primary/10 font-medium text-primary" : "text-foreground hover:bg-muted",
  )}>
    {pageIcon(page)}
    <span className="min-w-0 flex-1 truncate">{page.label}</span>
    <span aria-hidden="true" className="shrink-0 text-[11px] text-muted-foreground">{statusText(page)}</span>
  </button>;
}

function Section({ section, selectedPageId, onSelectPage }: Pick<WebsitePageTreeProps, "selectedPageId" | "onSelectPage"> & { section: WebsitePageTreeItem }) {
  const [open, setOpen] = useState(true);
  return (
    <Collapsible open={open} onOpenChange={setOpen}>
      <CollapsibleTrigger type="button" className="flex w-full items-center gap-2 rounded-lg px-2 py-2 text-left text-sm font-semibold outline-none transition-colors hover:bg-muted focus-visible:ring-2 focus-visible:ring-ring">
        <FolderTree className="size-3.5 shrink-0 text-primary" aria-hidden="true" />
        <span className="min-w-0 flex-1 truncate">{section.label}</span>
        <ChevronRight className={cn("size-4 text-muted-foreground transition-transform", open && "rotate-90")} aria-hidden="true" />
      </CollapsibleTrigger>
      <CollapsibleContent className="overflow-hidden">
        <ul className="mt-1 space-y-1 border-l border-border pl-3" aria-label={`${section.label} 페이지`}>
          {section.children.map((page) => <li key={page.id}><PageLeaf page={page} selectedPageId={selectedPageId} onSelectPage={onSelectPage} /></li>)}
          {section.children.length === 0 && <li className="px-2 py-1 text-xs text-muted-foreground">페이지가 없습니다.</li>}
        </ul>
      </CollapsibleContent>
    </Collapsible>
  );
}

export function WebsitePageTree({ pages, selectedPageId, onSelectPage, className }: WebsitePageTreeProps) {
  const home = pages.find((page) => page.pageType === "HOME_PAGE");
  const sections = pages.filter((page) => page.pageType === "SECTION");
  return (
    <nav aria-label="웹사이트 페이지" className={cn("rounded-xl border bg-card p-3 text-card-foreground shadow-sm", className)}>
      <p className="px-2 pb-2 text-xs font-medium tracking-wide text-muted-foreground">페이지 구조</p>
      <div className="space-y-1">
        {home && <ul aria-label="고정 페이지"><li><PageLeaf page={home} selectedPageId={selectedPageId} onSelectPage={onSelectPage} /></li></ul>}
        {sections.map((section) => <Section key={section.id} section={section} selectedPageId={selectedPageId} onSelectPage={onSelectPage} />)}
      </div>
      {pages.length === 0 && <p className="px-2 py-2 text-xs text-muted-foreground">등록된 페이지가 없습니다.</p>}
    </nav>
  );
}

export type { WebsitePageTreeProps };
