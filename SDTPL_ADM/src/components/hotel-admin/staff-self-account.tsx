"use client";

import { useEffect, useRef, useState } from "react";
import { UserCog } from "lucide-react";

import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  StaffApiError,
  updateOwnStaffAccount,
  type StaffPrincipal,
} from "@/lib/staff-api";

const roleLabels = new Map<string, string>([
  ["HQ_ADMIN", "본사 관리자"],
  ["HQ_EDITOR", "영문 편집자"],
  ["HQ_PUBLISHER", "영문 승인자"],
  ["BRANCH_STAFF", "지점 직원"],
]);

function roleLabel(role: string) {
  return roleLabels.get(role) ?? role;
}

export function StaffSelfAccount() {
  const [staff, setStaff] = useState<StaffPrincipal | null>(null);
  const [displayName, setDisplayName] = useState("");
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [open, setOpen] = useState(false);
  const [selfKey, setSelfKey] = useState(0);
  const mounted = useRef(false);

  const sessionToken =
    typeof window !== "undefined"
      ? window.localStorage.getItem("hotel-chain-staff-session")
      : null;

  useEffect(() => {
    mounted.current = true;
    const stored = window.localStorage.getItem("hotel-chain-staff");
    if (!stored) return;
    try {
      const parsed = JSON.parse(stored) as StaffPrincipal;
      setStaff(parsed);
      setDisplayName(parsed.displayName);
    } catch {
      setError("직원 정보를 읽지 못했습니다.");
    }
    return () => {
      mounted.current = false;
    };
  }, []);

  function openDialog() {
    setError("");
    setNotice("");
    setCurrentPassword("");
    setNewPassword("");
    setConfirmPassword("");
    setSelfKey((key) => key + 1);
    setOpen(true);
  }

  function closeDialog() {
    setOpen(false);
    setCurrentPassword("");
    setNewPassword("");
    setConfirmPassword("");
    setError("");
  }

  // 무엇을 바꾸는지 계산한다. 바꾸지 않는 필드는 서버에 보내지 않는다.
  function plannedChanges() {
    const trimmedName = displayName.trim();
    const wantsName =
      trimmedName.length > 0 && staff != null && trimmedName !== staff.displayName;
    const wantsPassword = newPassword.length > 0;
    return { wantsName, wantsPassword };
  }

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!sessionToken) return;
    const { wantsName, wantsPassword } = plannedChanges();
    if (!wantsName && !wantsPassword) {
      setError("바꿀 이름이나 새 비밀번호 중 하나는 입력해 주세요.");
      return;
    }
    if (wantsName && (displayName.trim().length < 1 || displayName.trim().length > 100)) {
      setError("이름은 1자 이상 100자 이하여야 합니다.");
      return;
    }
    if (wantsPassword) {
      if (newPassword.length < 8 || newPassword.length > 100) {
        setError("새 비밀번호는 8자 이상 100자 이하여야 합니다.");
        return;
      }
      if (newPassword !== confirmPassword) {
        setError("새 비밀번호와 확인 입력이 같지 않습니다.");
        return;
      }
      if (!currentPassword) {
        setError("새 비밀번호를 바꾸려면 현재 비밀번호를 입력해 주세요.");
        return;
      }
    }
    setSaving(true);
    setError("");
    try {
      // 본인 계정 수정은 매번 새 멱원 키를 쓴다. 재시도가 같은 키를
      // 재사용하지 않게 해서 비밀번호가 두 번 바뀌지 않는다.
      const result = await updateOwnStaffAccount(
        sessionToken,
        `self-update-${selfKey}`,
        {
          displayName: wantsName ? displayName.trim() : undefined,
          currentPassword: wantsPassword ? currentPassword : undefined,
          newPassword: wantsPassword ? newPassword : undefined,
        },
      );
      setSelfKey((key) => key + 1);
      const changedName = wantsName ? displayName.trim() : null;
      closeDialog();
      if (staff) {
        const next = { ...staff, displayName: result.displayName };
        setStaff(next);
        window.localStorage.setItem(
          "hotel-chain-staff",
          JSON.stringify(next),
        );
      }
      setNotice(
        changedName && wantsPassword
          ? `${result.email} 계정의 이름을 ${changedName}(으)로 바꾸고 비밀번호를 바꿨습니다. 다른 기기의 세션은 끊겼습니다.`
          : changedName
            ? `${result.email} 계정의 이름을 ${changedName}(으)로 바꿨습니다.`
            : `${result.email} 계정의 비밀번호를 바꿨습니다. 다른 기기의 세션은 끊겼습니다.`,
      );
    } catch (cause) {
      // 서버 검증 실패 시 대화상자를 닫지 않고 이유를 보여준다.
      setError(
        cause instanceof StaffApiError
          ? cause.message
          : "내 계정을 수정하지 못했습니다.",
      );
    } finally {
      if (mounted.current) setSaving(false);
    }
  }

  const { wantsName, wantsPassword } = plannedChanges();
  const submitLabel =
    wantsName && wantsPassword
      ? "이름·비밀번호 변경"
      : wantsName
        ? "이름 변경"
        : wantsPassword
          ? "비밀번호 변경"
          : "변경";

  return (
    <div className="flex flex-col gap-4">
      <div className="min-w-0">
        <h2 className="flex items-center gap-2 text-lg font-semibold">
          <UserCog className="h-5 w-5" aria-hidden />
          내 계정
        </h2>
        <p className="mt-1 max-w-[72ch] text-sm text-muted-foreground">
          본인 표시 이름과 비밀번호를 직접 바꾼다. 역할·소속 지점은 본사
          전용 권한이라 이 화면에서 바꿀 수 없다. 비밀번호 원문은 저장하지
          않는다.
        </p>
      </div>

      {error && (
        <p role="alert" className="break-words text-sm text-destructive">
          {error}
        </p>
      )}

      {notice && (
        <p
          role="status"
          data-testid="self-update-notice"
          className="break-words text-sm text-muted-foreground"
        >
          {notice}
        </p>
      )}

      {staff ? (
        <Card>
          <CardHeader>
            <CardTitle className="text-base">계정 정보</CardTitle>
            <CardDescription>
              이메일·역할·소속 지점은 읽기 전용이다.
            </CardDescription>
          </CardHeader>
          <CardContent className="grid gap-3 text-sm">
            <div className="grid gap-1">
              <Label htmlFor="self-email">이메일</Label>
              <Input
                id="self-email"
                value={staff.email}
                readOnly
                className="bg-muted/40"
                data-testid="self-email"
              />
            </div>
            <div className="grid gap-1">
              <Label htmlFor="self-name">표시 이름</Label>
              <Input
                id="self-name"
                value={displayName}
                onChange={(event) => setDisplayName(event.target.value)}
                data-testid="self-name"
              />
            </div>
            <div className="grid gap-1">
              <Label htmlFor="self-role">역할</Label>
              <Input
                id="self-role"
                value={roleLabel(staff.role)}
                readOnly
                className="bg-muted/40"
                data-testid="self-role"
              />
            </div>
            <div className="grid gap-1">
              <Label htmlFor="self-hotel">소속 지점</Label>
              <Input
                id="self-hotel"
                value={staff.hotelId ?? "본사"}
                readOnly
                className="bg-muted/40"
                data-testid="self-hotel"
              />
            </div>
            <div className="flex flex-wrap gap-2">
              <Button
                type="button"
                onClick={openDialog}
                disabled={saving}
                data-testid="open-self-update"
              >
                내 계정 수정
              </Button>
            </div>
          </CardContent>
        </Card>
      ) : (
        <p className="text-sm text-muted-foreground">
          직원 정보를 불러오는 중이거나 로그인이 필요합니다.
        </p>
      )}

      <Dialog
        open={open}
        onOpenChange={(next) => {
          if (!next) closeDialog();
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>내 계정 수정</DialogTitle>
            <DialogDescription>
              표시 이름과 비밀번호를 바꾼다. 역할·소속 지점은 본사 전용
              권한이다. 비밀번호를 바꾸면 다른 기기의 세션이 끊기고, 현재
              세션은 유지된다. 비밀번호를 바꿀 때는 현재 비밀번호 확인이
              필요하다.
            </DialogDescription>
          </DialogHeader>
          <form onSubmit={submit} className="grid gap-3">
            <label className="grid gap-1 text-sm font-medium">
              현재 비밀번호
              <Input
                type="password"
                autoComplete="current-password"
                value={currentPassword}
                onChange={(event) => setCurrentPassword(event.target.value)}
                data-testid="self-current-password"
              />
              <span className="text-xs font-normal text-muted-foreground">
                비밀번호를 바꿀 때만 필요하다.
              </span>
            </label>
            <label className="grid gap-1 text-sm font-medium">
              새 비밀번호
              <Input
                type="password"
                autoComplete="new-password"
                minLength={8}
                maxLength={100}
                value={newPassword}
                onChange={(event) => setNewPassword(event.target.value)}
                data-testid="self-new-password"
              />
              <span className="text-xs font-normal text-muted-foreground">
                8자 이상 100자 이하.
              </span>
            </label>
            <label className="grid gap-1 text-sm font-medium">
              새 비밀번호 확인
              <Input
                type="password"
                autoComplete="new-password"
                minLength={8}
                maxLength={100}
                value={confirmPassword}
                onChange={(event) => setConfirmPassword(event.target.value)}
                data-testid="self-confirm-password"
              />
            </label>
            {error ? (
              <p
                role="alert"
                className="break-words text-sm text-destructive"
                data-testid="self-update-error"
              >
                {error}
              </p>
            ) : null}
            <DialogFooter>
              <Button
                type="button"
                variant="outline"
                onClick={closeDialog}
                disabled={saving}
              >
                취소
              </Button>
              <Button
                type="submit"
                disabled={saving || (!wantsName && !wantsPassword)}
                data-testid="self-update-submit"
              >
                {saving ? "변경하는 중" : submitLabel}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  );
}
