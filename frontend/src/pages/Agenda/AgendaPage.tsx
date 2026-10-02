import { AppShell } from "../../components/AppShell/AppShell";

export function AgendaPage() {
  return (
    <AppShell>
      <main className="rv-page-frame">
        <header className="rv-page-header">
          <div>
            <p className="eyebrow">OPERACIÓN</p>
            <h1>Agenda</h1>
            <p>Gestiona tus citas y reservas</p>
          </div>
        </header>
      </main>
    </AppShell>
  );
}
