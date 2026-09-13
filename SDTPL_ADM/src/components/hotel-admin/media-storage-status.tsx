"use client";

import { useCallback, useEffect, useRef, useState } from "react";

import { Button } from "@/components/ui/button";
import {
  backfillWebsiteMediaStorage,
  getWebsiteMediaStorageAudit,
  getWebsiteMediaStorageMigration,
  type WebsiteMediaStorageAudit,
  type WebsiteMediaStorageMigrationStatus,
} from "@/lib/staff-api";

function modeLabel(mode: WebsiteMediaStorageAudit["mode"]) {
  if (mode === "s3-primary") return "S3-primary";
  return mode;
}

function storeLabel(storeName: string) {
  return storeName === "s3" ? "S3" : "로컬";
}

export function MediaStorageStatus({ token, active }: { token: string; active: boolean }) {
  const [audit, setAudit] = useState<WebsiteMediaStorageAudit | null>(null);
  const [migration, setMigration] = useState<WebsiteMediaStorageMigrationStatus | null>(null);
  const [loading, setLoading] = useState(false);
  const [backfilling, setBackfilling] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const requestGeneration = useRef(0);
  const activeRef = useRef(active);

  useEffect(() => {
    activeRef.current = active;
    if (!active) requestGeneration.current += 1;
  }, [active]);

  useEffect(() => () => {
    activeRef.current = false;
    requestGeneration.current += 1;
  }, []);

  const refresh = useCallback(async (preserveFeedback = false) => {
    const generation = ++requestGeneration.current;
    setLoading(true);
    if (!preserveFeedback) {
      setError("");
      setNotice("");
    }
    try {
      const nextAudit = await getWebsiteMediaStorageAudit(token);
      if (!activeRef.current || requestGeneration.current !== generation) return;
      setAudit(nextAudit);
      if (nextAudit.mode === "local") {
        setMigration(null);
        return;
      }
      try {
        const nextMigration = await getWebsiteMediaStorageMigration(token);
        if (activeRef.current && requestGeneration.current === generation) setMigration(nextMigration);
      } catch (cause) {
        if (activeRef.current && requestGeneration.current === generation) {
          setMigration(null);
          setError(cause instanceof Error ? cause.message : "저장소 이전 상태를 불러오지 못했습니다.");
        }
      }
    } catch (cause) {
      if (activeRef.current && requestGeneration.current === generation) {
        setAudit(null);
        setMigration(null);
        setError(cause instanceof Error ? cause.message : "미디어 저장소를 점검하지 못했습니다.");
      }
    } finally {
      if (activeRef.current && requestGeneration.current === generation) setLoading(false);
    }
  }, [token]);

  async function backfill() {
    const generation = ++requestGeneration.current;
    setBackfilling(true);
    setError("");
    setNotice("");
    try {
      const result = await backfillWebsiteMediaStorage(token);
      if (!activeRef.current || requestGeneration.current !== generation) return;
      setNotice(`${result.copied}개를 S3에 복사했습니다.`);
      if (result.failed > 0) {
        setError(`복사하지 못한 파일이 ${result.failed}개 있습니다. 저장소 점검 후 다시 시도해 주세요.`);
      } else if (result.mismatch > 0) {
        setError(`체크섬이 다른 파일이 ${result.mismatch}개 있습니다. 파일을 확인해 주세요.`);
      }
      setBackfilling(false);
      await refresh(true);
    } catch (cause) {
      if (activeRef.current && requestGeneration.current === generation) {
        setError(cause instanceof Error ? cause.message : "S3 백필을 실행하지 못했습니다.");
        setBackfilling(false);
      }
    }
  }

  const legacySummary = audit && !audit.healthy
    ? `누락 ${audit.missingStorageKeys.length}개 · orphan ${audit.orphanStorageKeys.length}개 · 오래된 임시 파일 ${audit.staleTemporaryStorageKeys.length}개`
    : "";

  return (
    <section aria-label="미디어 저장소 점검" aria-busy={loading || backfilling} className="mb-5 border-b pb-5">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0">
          <h3 className="font-medium">미디어 저장소 점검</h3>
          <p className="mt-1 max-w-[72ch] text-sm text-muted-foreground">
            DB 참조와 실제 파일을 비교합니다. 점검은 파일을 변경하지 않습니다.
          </p>
        </div>
        <Button type="button" variant="outline" onClick={() => void refresh()} disabled={loading || backfilling}>
          {loading ? "점검 중" : "저장소 점검"}
        </Button>
      </div>

      {error && <p role="alert" className="mt-3 break-words text-sm text-destructive">{error}</p>}
      {migration && migration.mismatch > 0 && !error && (
        <p role="alert" className="mt-3 text-sm text-destructive">
          체크섬이 다른 파일이 {migration.mismatch}개 있습니다. 자동으로 덮어쓰지 않습니다.
        </p>
      )}

      {(audit || notice) && (
        <div role="status" className="mt-3 text-sm">
          {migration && <p className="font-medium tabular-nums">{modeLabel(migration.mode)} · 양쪽 일치 {migration.both}개</p>}
          {!migration && audit?.healthy && <p className="text-emerald-700">DB 참조와 저장 파일이 모두 일치합니다.</p>}
          {!migration && legacySummary && <p className="font-medium tabular-nums">{legacySummary}</p>}
          {notice && <p className="mt-2 text-emerald-700">{notice}</p>}
        </div>
      )}

      {migration && (
        <div className="mt-3 flex flex-wrap gap-x-4 gap-y-1 text-sm text-muted-foreground tabular-nums">
          <span>S3에 없는 파일 {migration.localOnly}개</span>
          <span>로컬에 없는 파일 {migration.s3Only}개</span>
          <span>양쪽 모두 없음 {migration.missing}개</span>
          {migration.fallbackCount > 0 && <span>S3 읽기 대체 {migration.fallbackCount}회</span>}
        </div>
      )}

      {audit && (
        <div className="mt-4 grid min-w-0 gap-4 sm:grid-cols-2">
          {audit.stores.map((store) => {
            const groups = [
              { label: "누락된 참조 파일", keys: store.missingStorageKeys },
              { label: "참조 없는 파일", keys: store.orphanStorageKeys },
              { label: "오래된 임시 파일", keys: store.staleTemporaryStorageKeys },
            ];
            return (
              <section key={store.storeName} className="min-w-0 border-t pt-3">
                <h4 className="text-sm font-medium">{storeLabel(store.storeName)} · {store.healthy ? "정상" : "확인 필요"}</h4>
                <div className="mt-2 grid gap-2">
                  {groups.map((group) => group.keys.length > 0 && (
                    <div key={group.label} className="min-w-0">
                      <p className="text-xs font-medium text-muted-foreground">{group.label}</p>
                      <ul className="mt-1 grid gap-1">
                        {group.keys.map((key) => <li key={key}><code className="break-all text-xs">{key}</code></li>)}
                      </ul>
                    </div>
                  ))}
                  {groups.every((group) => group.keys.length === 0) && (
                    <p className="text-xs text-muted-foreground">문제 없음</p>
                  )}
                </div>
              </section>
            );
          })}
        </div>
      )}

      {audit?.mode === "mirror" && (
        <div className="mt-4 border-t pt-4">
          <p className="text-sm text-muted-foreground">
            기존 로컬 파일은 삭제하지 않고 S3에 추가 사본을 만듭니다.
          </p>
          <Button className="mt-3" type="button" onClick={() => void backfill()} disabled={loading || backfilling}>
            {backfilling ? "복사 중" : "다음 100개 복사"}
          </Button>
        </div>
      )}
    </section>
  );
}
