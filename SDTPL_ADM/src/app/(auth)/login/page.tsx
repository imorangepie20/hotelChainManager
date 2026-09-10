"use client"

import { FormEvent, useState } from "react"
import { LoaderCircle } from "lucide-react"

import { AuthCard } from "@/components/auth/auth-card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { loginStaff, StaffApiError } from "@/lib/staff-api"

export default function LoginPage() {
  const [email, setEmail] = useState("")
  const [password, setPassword] = useState("")
  const [error, setError] = useState<string>()
  const [pending, setPending] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setPending(true)
    setError(undefined)
    try {
      const session = await loginStaff(email, password)
      window.localStorage.setItem("hotel-chain-staff-session", session.token)
      window.localStorage.setItem("hotel-chain-staff", JSON.stringify(session.staff))
      window.location.assign("/dashboard/default")
    } catch (reason) {
      setError(reason instanceof StaffApiError ? reason.message : "\uB85C\uADF8\uC778 \uC694\uCCAD\uC744 \uCC98\uB9AC\uD558\uC9C0 \uBABB\uD588\uC2B5\uB2C8\uB2E4.")
    } finally {
      setPending(false)
    }
  }

  return (
    <AuthCard
      title={"\uC6B4\uC601 \uAD00\uB9AC\uC790 \uB85C\uADF8\uC778"}
      description={"\uBCF8\uC0AC\uC640 \uD638\uD154 \uC9C0\uC810\uC758 \uC6B4\uC601 \uC5C5\uBB34\uB97C \uAD00\uB9AC\uD569\uB2C8\uB2E4."}
    >
      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="email">{"\uC774\uBA54\uC77C"}</Label>
          <Input
            id="email"
            type="email"
            autoComplete="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            required
          />
        </div>

        <div className="flex flex-col gap-1.5">
          <Label htmlFor="password">{"\uBE44\uBC00\uBC88\uD638"}</Label>
          <Input
            id="password"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            required
          />
        </div>

        {error && <p role="alert" className="text-sm text-destructive">{error}</p>}

        <Button type="submit" className="mt-1 w-full" disabled={pending}>
          {pending && <LoaderCircle className="animate-spin" />} {"\uB85C\uADF8\uC778"}
        </Button>
      </form>
    </AuthCard>
  )
}
