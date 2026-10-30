import { Suspense } from "react";
import { AuthForm } from "@/components/auth/AuthForm";
export default function Page() {
  return (
    <Suspense
      fallback={
        <div className="page-shell" role="status">
          Carregando…
        </div>
      }
    >
      <AuthForm mode="reset" />
    </Suspense>
  );
}
