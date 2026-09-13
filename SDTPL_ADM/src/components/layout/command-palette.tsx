"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Search } from "lucide-react";
import { useStaffNavigation } from "@/hooks/use-staff-navigation";
import { Button } from "@/components/ui/button";
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from "@/components/ui/command";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";

export function CommandPalette() {
  const [open, setOpen] = useState(false);
  const router = useRouter();
  const navGroups = useStaffNavigation();

  useEffect(() => {
    const down = (e: KeyboardEvent) => {
      if (e.key === "k" && (e.metaKey || e.ctrlKey)) {
        e.preventDefault();
        setOpen((o) => !o);
      }
    };
    document.addEventListener("keydown", down);
    return () => document.removeEventListener("keydown", down);
  }, []);

  return (
    <>
      <Button
        variant="outline"
        aria-label="메뉴 검색"
        className="relative size-8 px-0 text-muted-foreground sm:h-9 sm:w-64 sm:justify-start sm:px-3"
        onClick={() => setOpen(true)}
      >
        <Search className="size-4" />
        <span className="ml-2 hidden sm:inline">메뉴 검색…</span>
        <kbd className="pointer-events-none absolute right-2 top-2 hidden h-5 select-none items-center gap-1 rounded border bg-muted px-1.5 text-[10px] font-medium sm:flex">
          ⌘K
        </kbd>
      </Button>
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent
          className="top-1/3 translate-y-0 overflow-hidden rounded-xl! p-0"
          showCloseButton={false}
        >
          <DialogHeader className="sr-only">
            <DialogTitle>메뉴 검색</DialogTitle>
            <DialogDescription>이동할 관리자 메뉴를 검색합니다.</DialogDescription>
          </DialogHeader>
          <Command>
            <CommandInput placeholder="메뉴 이름을 입력하세요…" />
            <CommandList>
              <CommandEmpty>검색 결과가 없습니다.</CommandEmpty>
              {navGroups.map((group) => (
                <CommandGroup key={group.label} heading={group.label}>
                  {group.items.map((item) => (
                    <CommandItem
                      key={item.href}
                      value={`${group.label} ${item.title}`}
                      onSelect={() => {
                        setOpen(false);
                        router.push(item.href);
                      }}
                    >
                      {item.icon && <item.icon className="size-4" />}
                      <span>{item.title}</span>
                    </CommandItem>
                  ))}
                </CommandGroup>
              ))}
            </CommandList>
          </Command>
        </DialogContent>
      </Dialog>
    </>
  );
}
