"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import {
  KeyRound,
  Pencil,
  Plus,
  RefreshCw,
  UserX,
  UserCheck,
  Users,
  Trash2,
} from "lucide-react";

import { Badge } from "@/components/ui/badge";
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
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  createStaffAccount,
  deleteStaffAccount,
  getStaffAccounts,
  resetStaffPassword,
  setStaffAccountActive,
  StaffApiError,
  updateStaffAccount,
  type StaffAccountView,
  type StaffPrincipal,
} from "@/lib/staff-api";

const hotels = [
  { id: "11000000-0000-0000-0000-000000000001", name: "속초 지점" },
  { id: "11000000-0000-0000-0000-000000000002", name: "설악산 지점" },
  { id: "11000000-0000-0000-0000-000000000003", name: "제주 지점" },
];

const roleLabels = new Map<string, string>([
  ["HQ_ADMIN", "본사 관리자"],
  ["HQ_EDITOR", "영문 편집자"],
  ["HQ_PUBLISHER", "영문 승인자"],
  ["BRANCH_STAFF", "지점 직원"],
]);

const headquartersRoles = ["HQ_ADMIN", "HQ_EDITOR", "HQ_PUBLISHER"];

const STAFF_ROLES: StaffRole[] = [
  "HQ_ADMIN",
  "HQ_EDITOR",
  "HQ_PUBLISHER",
  "BRANCH_STAFF",
];

type StaffRole = "HQ_ADMIN" | "HQ_EDITOR" | "HQ_PUBLISHER" | "BRANCH_STAFF";

function roleLabel(role: string) {
  return roleLabels.get(role) ?? role;
}

export function StaffAccounts() {
  const [staff, setStaff] = useState<StaffPrincipal | null>(null);
  const [accounts, setAccounts] = useState<StaffAccountView[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [createOpen, setCreateOpen] = useState(false);
  const [email, setEmail] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [role, setRole] = useState<StaffRole>("HQ_EDITOR");
  const [hotelId, setHotelId] = useState<string>("");
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState("");
  const [issued, setIssued] = useState<{
    email: string;
    password: string;
  } | null>(null);
  const [createKey, setCreateKey] = useState(0);
  const [resetting, setResetting] = useState(false);
  const [resetError, setResetError] = useState("");
  // 비활성·재활성은 매번 새 멱원 키를 쓴다. 같은 키 재호출과 구분하기 위해서다.
  const [activationKey, setActivationKey] = useState(0);
  const [activating, setActivating] = useState(false);
  const [activationError, setActivationError] = useState("");
  // 삭제는 매번 새 멱원 키를 쓴다. 되돌릴 수 없는 동작이므로 재시도가
  // 같은 키를 재사용하지 않게 한다.
  const [deleteTarget, setDeleteTarget] = useState<StaffAccountView | null>(
    null,
  );
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState("");
  const [deleteNotice, setDeleteNotice] = useState("");
  const [deleteKey, setDeleteKey] = useState(0);
  const requestGeneration = useRef(0);
  const mounted = useRef(false);

  const isHeadquarters = staff?.role === "HQ_ADMIN";
  const sessionToken =
    typeof window !== "undefined"
      ? window.localStorage.getItem("hotel-chain-staff-session")
      : null;

  useEffect(() => {
    mounted.current = true;
    const stored = window.localStorage.getItem("hotel-chain-staff");
    if (!stored) return;
    try {
      setStaff(JSON.parse(stored) as StaffPrincipal);
      requestGeneration.current += 1;
    } catch {
      setError("직원 정보를 읽지 못했습니다.");
    }
    return () => {
      mounted.current = false;
      requestGeneration.current += 1;
    };
  }, []);

  const refresh = useCallback(async () => {
    const generation = ++requestGeneration.current;
    if (!sessionToken) return;
    setLoading(true);
    setError("");
    try {
      const next = await getStaffAccounts(sessionToken);
      if (!mounted.current || requestGeneration.current !== generation) return;
      setAccounts(next);
    } catch (cause) {
      if (mounted.current && requestGeneration.current === generation) {
        setAccounts([]);
        setError(
          cause instanceof StaffApiError
            ? cause.message
            : "직원 목록을 불러오지 못했습니다.",
        );
      }
    } finally {
      if (mounted.current && requestGeneration.current === generation)
        setLoading(false);
    }
  }, [sessionToken]);

  useEffect(() => {
    if (!isHeadquarters) return;
    void refresh();
  }, [isHeadquarters, refresh]);

  const isBranch = !headquartersRoles.includes(role);

  async function submitCreate(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!sessionToken) return;
    const trimmedEmail = email.trim();
    const trimmedName = displayName.trim();
    if (!trimmedEmail || !trimmedEmail.includes("@")) {
      setCreateError("올바른 이메일 형식을 입력해 주세요.");
      return;
    }
    if (!trimmedName) {
      setCreateError("이름을 입력해 주세요.");
      return;
    }
    if (isBranch && !hotelId) {
      setCreateError("지점 직원은 소속 지점을 선택해 주세요.");
      return;
    }
    setCreating(true);
    setCreateError("");
    try {
      const result = await createStaffAccount(
        sessionToken,
        `create-staff-${createKey}`,
        {
          email: trimmedEmail,
          displayName: trimmedName,
          role,
          hotelId: isBranch ? hotelId : null,
        },
      );
      setCreateKey((key) => key + 1);
      setEmail("");
      setDisplayName("");
      setRole("HQ_EDITOR");
      setHotelId("");
      setCreateOpen(false);
      if (result.temporaryPassword) {
        setIssued({ email: result.email, password: result.temporaryPassword });
      }
      await refresh();
    } catch (cause) {
      setCreateError(
        cause instanceof StaffApiError
          ? cause.message
          : "직원을 추가하지 못했습니다.",
      );
    } finally {
      setCreating(false);
    }
  }

  function openCreateDialog() {
    setCreateError("");
    setIssued(null);
    setCreateOpen(true);
  }

  // 재발급은 매번 새 멱원 키를 쓴다. 같은 키 재호출과 구분하기 위해서다.
  const [resetKey, setResetKey] = useState(0);
  const resetIdempotencyKeys = useRef<Record<string, string>>({});

  // 역할·소속 지점 수정. 대화상자를 열 때마다 새 멱원 키를 쓴다.
  const [editTarget, setEditTarget] = useState<StaffAccountView | null>(null);
  const [editRole, setEditRole] = useState<StaffRole>("HQ_EDITOR");
  const [editHotelId, setEditHotelId] = useState<string>("");
  const [editOpen, setEditOpen] = useState(false);
  const [editing, setEditing] = useState(false);
  const [editError, setEditError] = useState("");
  const [editKey, setEditKey] = useState(0);

  const editIsBranch = !headquartersRoles.includes(editRole);

  function openEditDialog(account: StaffAccountView) {
    setEditError("");
    setEditTarget(account);
    setEditRole(account.role);
    setEditHotelId(account.hotelId ?? "");
    setEditKey((key) => key + 1);
    setEditOpen(true);
  }

  function closeEditDialog() {
    setEditOpen(false);
    setEditTarget(null);
    setEditError("");
  }

  async function submitEdit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!sessionToken || !editTarget) return;
    if (editIsBranch && !editHotelId) {
      setEditError("지점 직원은 소속 지점을 선택해 주세요.");
      return;
    }
    setEditing(true);
    setEditError("");
    try {
      await updateStaffAccount(
        sessionToken,
        editTarget.id,
        `update-staff-${editTarget.id}-${editKey}`,
        {
          role: editRole,
          hotelId: editIsBranch ? editHotelId : null,
        },
      );
      closeEditDialog();
      await refresh();
    } catch (cause) {
      setEditError(
        cause instanceof StaffApiError
          ? cause.message
          : "직원 정보를 수정하지 못했습니다.",
      );
    } finally {
      setEditing(false);
    }
  }

  async function resetPassword(account: StaffAccountView) {
    if (!sessionToken) return;
    const idempotencyKey = `reset-password-${account.id}-${resetKey}`;
    resetIdempotencyKeys.current[account.id] = idempotencyKey;
    setResetKey((key) => key + 1);
    setResetting(true);
    setResetError("");
    try {
      const result = await resetStaffPassword(
        sessionToken,
        account.id,
        idempotencyKey,
      );
      if (result.temporaryPassword) {
        setIssued({ email: result.email, password: result.temporaryPassword });
      } else {
        // 멱원 재호출이나 쿨다운으로 비밀번호를 받지 못한 대화상자를 연다.
        setIssued({ email: result.email, password: "" });
      }
    } catch (cause) {
      setResetError(
        cause instanceof StaffApiError
          ? cause.message
          : "비밀번호를 재발급하지 못했습니다.",
      );
    } finally {
      setResetting(false);
    }
  }

  // 비활성 직원의 세션은 서버가 즉시 끊는다. 재활성하면 다시 로그인할 수 있다.
  async function toggleActive(account: StaffAccountView) {
    if (!sessionToken) return;
    const next = !account.active;
    setActivating(true);
    setActivationError("");
    try {
      await setStaffAccountActive(
        sessionToken,
        account.id,
        `activate-staff-${account.id}-${activationKey}`,
        next,
      );
      setActivationKey((key) => key + 1);
      await refresh();
    } catch (cause) {
      setActivationError(
        cause instanceof StaffApiError
          ? cause.message
          : "활성 상태를 바꾸지 못했습니다.",
      );
    } finally {
      if (mounted.current) setActivating(false);
    }
  }

  function openDeleteDialog(account: StaffAccountView) {
    setDeleteError("");
    setDeleteNotice("");
    setDeleteTarget(account);
    setDeleteKey((key) => key + 1);
    setDeleteOpen(true);
  }

  function closeDeleteDialog() {
    setDeleteOpen(false);
    setDeleteTarget(null);
    setDeleteError("");
  }

  async function submitDelete(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!sessionToken || !deleteTarget) return;
    setDeleting(true);
    setDeleteError("");
    try {
      // 삭제는 매번 새 멱원 키를 쓴다. 네트워크 장애 뒤 다시 눌러도
      // 중복 삭제 시도가 생기지 않는다.
      const result = await deleteStaffAccount(
        sessionToken,
        deleteTarget.id,
        `delete-staff-${deleteTarget.id}-${deleteKey}`,
      );
      setDeleteKey((key) => key + 1);
      const deletedName = deleteTarget.displayName;
      const deletedEmail = deleteTarget.email;
      closeDeleteDialog();
      await refresh();
      setDeleteNotice(
        `${deletedName} (${deletedEmail}) 계정을 삭제했습니다. 남은 직원은 ${result.remainingStaff}명입니다. 진행 중인 예약 변경 요청과 정산 실행의 담당자 표시는 삭제된 직원으로 남습니다.`,
      );
    } catch (cause) {
      // 서버 검증 실패 시 대화상자를 닫지 않고 이유를 보여준다.
      setDeleteError(
        cause instanceof StaffApiError
          ? cause.message
          : "직원을 삭제하지 못했습니다.",
      );
    } finally {
      if (mounted.current) setDeleting(false);
    }
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0">
          <h2 className="flex items-center gap-2 text-lg font-semibold">
            <Users className="h-5 w-5" aria-hidden />
            직원 권한
          </h2>
          <p className="mt-1 max-w-[72ch] text-sm text-muted-foreground">
            직원 목록을 읽고 새 직원을 추가하며 역할·소속 지점을 바꾸고 임시
            비밀번호를 재발급한다. 비밀번호 원문은 저장하지 않는다.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button
            type="button"
            variant="outline"
            onClick={() => void refresh()}
            disabled={loading || !isHeadquarters}
          >
            <RefreshCw className="h-4 w-4" aria-hidden />
            {loading ? "불러오는 중" : "새로고침"}
          </Button>
          {isHeadquarters && (
            <Button
              type="button"
              onClick={openCreateDialog}
              data-testid="create-staff"
            >
              <Plus className="h-4 w-4" aria-hidden />
              직원 추가
            </Button>
          )}
        </div>
      </div>

      {error && (
        <p role="alert" className="break-words text-sm text-destructive">
          {error}
        </p>
      )}

      {resetError && (
        <p
          role="alert"
          className="break-words text-sm text-destructive"
          data-testid="reset-error"
        >
          {resetError}
        </p>
      )}

      {activationError && (
        <p
          role="alert"
          className="break-words text-sm text-destructive"
          data-testid="activation-error"
        >
          {activationError}
        </p>
      )}

      {deleteNotice && isHeadquarters && (
        <p
          role="status"
          data-testid="delete-staff-notice"
          className="break-words text-sm text-muted-foreground"
        >
          {deleteNotice}
        </p>
      )}

      {deleteError && isHeadquarters && (
        <p
          role="alert"
          data-testid="delete-staff-error"
          className="break-words text-sm text-destructive"
        >
          {deleteError}
        </p>
      )}

      {staff && !isHeadquarters && (
        <p role="status" className="text-sm text-muted-foreground">
          직원 관리는 본사 관리자만 할 수 있습니다.
        </p>
      )}

      {isHeadquarters && accounts.length === 0 && !error && !loading && (
        <p className="text-sm text-muted-foreground">등록된 직원이 없습니다.</p>
      )}

      {isHeadquarters && accounts.length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle className="text-base">
              직원 {accounts.length}명
            </CardTitle>
            <CardDescription>
              이메일·이름·역할·소속 지점·활성 상태를 보여준다. 비밀번호는
              표시하지 않는다.
            </CardDescription>
          </CardHeader>
          <CardContent className="overflow-x-auto p-0">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>이메일</TableHead>
                  <TableHead>이름</TableHead>
                  <TableHead>역할</TableHead>
                  <TableHead>소속 지점</TableHead>
                  <TableHead>활성</TableHead>
                  <TableHead className="text-right">역할·지점</TableHead>
                  <TableHead className="text-right">비밀번호</TableHead>
                  <TableHead className="text-right">삭제</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {accounts.map((account) => (
                  <TableRow key={account.id} data-staff-id={account.id}>
                    <TableCell className="font-medium break-all">
                      {account.email}
                    </TableCell>
                    <TableCell>{account.displayName}</TableCell>
                    <TableCell>
                      <Badge
                        variant={
                          account.role === "HQ_ADMIN" ? "default" : "secondary"
                        }
                      >
                        {roleLabel(account.role)}
                      </Badge>
                    </TableCell>
                    <TableCell className="text-muted-foreground">
                      {account.hotelName ?? "본사"}
                    </TableCell>
                    <TableCell>
                      <Badge variant={account.active ? "default" : "secondary"}>
                        {account.active ? "활성" : "비활성"}
                      </Badge>
                    </TableCell>
                    <TableCell className="text-right">
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        onClick={() => openEditDialog(account)}
                        disabled={editing}
                        aria-label={`${account.displayName} 역할·소속 지점 수정`}
                        data-testid={`edit-staff-${account.id}`}
                      >
                        <Pencil className="h-4 w-4" aria-hidden />
                        수정
                      </Button>
                    </TableCell>
                    <TableCell className="text-right">
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        onClick={() => void toggleActive(account)}
                        disabled={activating}
                        aria-label={
                          account.active
                            ? `${account.displayName} 비활성`
                            : `${account.displayName} 재활성`
                        }
                        data-testid={`toggle-active-${account.id}`}
                      >
                        {account.active ? (
                          <>
                            <UserX className="h-4 w-4" aria-hidden />
                            비활성
                          </>
                        ) : (
                          <>
                            <UserCheck className="h-4 w-4" aria-hidden />
                            재활성
                          </>
                        )}
                      </Button>
                    </TableCell>
                    <TableCell className="text-right">
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        onClick={() => void resetPassword(account)}
                        disabled={resetting}
                        aria-label={`${account.displayName} 임시 비밀번호 재발급`}
                        data-testid={`reset-password-${account.id}`}
                      >
                        <KeyRound className="h-4 w-4" aria-hidden />
                        재발급
                      </Button>
                    </TableCell>
                    <TableCell className="text-right">
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        className="text-destructive"
                        onClick={() => openDeleteDialog(account)}
                        disabled={deleting}
                        aria-label={`${account.displayName} 삭제`}
                        data-testid={`delete-staff-${account.id}`}
                      >
                        <Trash2 className="h-4 w-4" aria-hidden />
                        삭제
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      )}

      <Dialog
        open={!!issued}
        onOpenChange={(open) => {
          if (!open) setIssued(null);
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>임시 비밀번호 발급</DialogTitle>
            <DialogDescription>
              {issued?.password
                ? "직원이 로그인할 때 사용한다. 이 화면을 닫으면 다시 볼 수 없으므로 안전하게 전달해 주세요."
                : "재발급된 비밀번호는 다시 볼 수 없습니다. 잠시 후 다시 재발급해 주세요."}
            </DialogDescription>
          </DialogHeader>
          <div className="grid gap-3">
            <p className="text-sm text-muted-foreground">대상 이메일</p>
            <p className="break-all font-medium">{issued?.email}</p>
            {issued?.password && (
              <>
                <p className="text-sm text-muted-foreground">임시 비밀번호</p>
                <p
                  className="break-all rounded-lg border bg-muted/40 p-3 font-mono text-sm"
                  data-testid="temporary-password"
                >
                  {issued.password}
                </p>
              </>
            )}
            {!issued?.password && (
              <p
                className="break-words rounded-lg border bg-muted/40 p-3 text-sm"
                data-testid="temporary-password-unavailable"
              >
                재발급 직후에는 같은 직원의 비밀번호를 다시 발급할 수 없습니다.
              </p>
            )}
          </div>
          <DialogFooter>
            <Button type="button" onClick={() => setIssued(null)}>
              확인
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={createOpen} onOpenChange={setCreateOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>직원 추가</DialogTitle>
            <DialogDescription>
              이메일·이름·역할·소속 지점을 입력한다. 본사 역할은 지점을 가질 수
              없다.
            </DialogDescription>
          </DialogHeader>
          <form onSubmit={submitCreate} className="grid gap-3">
            <label className="grid gap-1 text-sm font-medium">
              이메일
              <input
                aria-label="이메일"
                type="email"
                required
                maxLength={254}
                className="h-9 rounded-lg border bg-background px-3"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                data-testid="create-staff-email"
              />
            </label>
            <label className="grid gap-1 text-sm font-medium">
              이름
              <input
                aria-label="이름"
                type="text"
                required
                maxLength={100}
                className="h-9 rounded-lg border bg-background px-3"
                value={displayName}
                onChange={(event) => setDisplayName(event.target.value)}
                data-testid="create-staff-name"
              />
            </label>
            <label className="grid gap-1 text-sm font-medium">
              역할
              <select
                aria-label="역할"
                className="h-9 rounded-lg border bg-background px-3"
                value={role}
                onChange={(event) => setRole(event.target.value as StaffRole)}
                data-testid="create-staff-role"
              >
                {STAFF_ROLES.map((value) => (
                  <option key={value} value={value}>
                    {roleLabel(value)}
                  </option>
                ))}
              </select>
            </label>
            {isBranch && (
              <label className="grid gap-1 text-sm font-medium">
                소속 지점
                <select
                  aria-label="소속 지점"
                  required
                  className="h-9 rounded-lg border bg-background px-3"
                  value={hotelId}
                  onChange={(event) => setHotelId(event.target.value)}
                  data-testid="create-staff-hotel"
                >
                  <option value="">선택하세요</option>
                  {hotels.map((hotel) => (
                    <option key={hotel.id} value={hotel.id}>
                      {hotel.name}
                    </option>
                  ))}
                </select>
              </label>
            )}
            {createError && (
              <p role="alert" className="break-words text-sm text-destructive">
                {createError}
              </p>
            )}
            <DialogFooter>
              <Button
                type="button"
                variant="outline"
                onClick={() => setCreateOpen(false)}
                disabled={creating}
              >
                취소
              </Button>
              <Button
                type="submit"
                disabled={creating || !email.trim() || !displayName.trim()}
                data-testid="create-staff-submit"
              >
                {creating ? "추가하는 중" : "추가"}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <Dialog
        open={editOpen}
        onOpenChange={(open) => {
          if (!open) closeEditDialog();
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>역할·소속 지점 수정</DialogTitle>
            <DialogDescription>
              {editTarget?.email} ({editTarget?.displayName})의 역할과 소속
              지점을 바꾼다. 본사 역할은 지점을 가질 수 없다. 역할을 바꾸면 다음
              로그인부터 적용된다.
            </DialogDescription>
          </DialogHeader>
          <form onSubmit={submitEdit} className="grid gap-3">
            <label className="grid gap-1 text-sm font-medium">
              역할
              <select
                aria-label="역할"
                className="h-9 rounded-lg border bg-background px-3"
                value={editRole}
                onChange={(event) =>
                  setEditRole(event.target.value as StaffRole)
                }
                data-testid="edit-staff-role"
              >
                {STAFF_ROLES.map((value) => (
                  <option key={value} value={value}>
                    {roleLabel(value)}
                  </option>
                ))}
              </select>
            </label>
            {editIsBranch && (
              <label className="grid gap-1 text-sm font-medium">
                소속 지점
                <select
                  aria-label="소속 지점"
                  required
                  className="h-9 rounded-lg border bg-background px-3"
                  value={editHotelId}
                  onChange={(event) => setEditHotelId(event.target.value)}
                  data-testid="edit-staff-hotel"
                >
                  <option value="">선택하세요</option>
                  {hotels.map((hotel) => (
                    <option key={hotel.id} value={hotel.id}>
                      {hotel.name}
                    </option>
                  ))}
                </select>
              </label>
            )}
            {editError && (
              <p
                role="alert"
                className="break-words text-sm text-destructive"
                data-testid="edit-error"
              >
                {editError}
              </p>
            )}
            <DialogFooter>
              <Button
                type="button"
                variant="outline"
                onClick={closeEditDialog}
                disabled={editing}
              >
                취소
              </Button>
              <Button
                type="submit"
                disabled={editing}
                data-testid="edit-staff-submit"
              >
                {editing ? "수정하는 중" : "수정"}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <Dialog
        open={deleteOpen}
        onOpenChange={(open) => {
          if (!open) closeDeleteDialog();
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>직원 계정 삭제</DialogTitle>
            <DialogDescription>
              {deleteTarget
                ? `${deleteTarget.email} (${deleteTarget.displayName}) 계정을 영구 삭제한다.`
                : "직원을 선택해 주세요."}
              이 작업은 되돌릴 수 없다. 로그인할 수 없게 되고 직원 목록에서
              빠진다. 진행 중인 예약 변경 요청·정산 실행의 담당자 표시는
              삭제된 직원으로 남는다. 잠시 멈추는 것이 목적이면 비활성을
              먼저 쓴다.
            </DialogDescription>
          </DialogHeader>
          <form onSubmit={submitDelete} className="grid gap-3">
            {deleteError ? (
              <p
                role="alert"
                className="break-words text-sm text-destructive"
                data-testid="delete-staff-form-error"
              >
                {deleteError}
              </p>
            ) : null}
            <DialogFooter>
              <Button
                type="button"
                variant="outline"
                onClick={closeDeleteDialog}
                disabled={deleting}
              >
                취소
              </Button>
              <Button
                type="submit"
                variant="destructive"
                disabled={deleting}
                data-testid="delete-staff-submit"
              >
                {deleting ? "삭제하는 중" : "삭제"}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  );
}
