import { useState } from "react";
import { createCustomer, type Customer } from "./api";
import styles from "./CustomerQuickCreate.module.css";

interface CustomerQuickCreateProps {
  onCreated(customer: Customer): void | Promise<void>;
}

export function CustomerQuickCreate({ onCreated }: CustomerQuickCreateProps) {
  const [open, setOpen] = useState(false);
  const [name, setName] = useState("");
  const [phone, setPhone] = useState("");
  const [email, setEmail] = useState("");
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState("");

  async function submit() {
    if (saving) return;
    if (!name.trim() && !phone.trim() && !email.trim()) {
      setMessage("Ingresa al menos un dato del cliente.");
      return;
    }

    setSaving(true);
    setMessage("");
    try {
      const customer = await createCustomer({
        name: name.trim() || null,
        phone: phone.trim() || null,
        email: email.trim() || null,
        notes: null
      });
      await onCreated(customer);
      setName("");
      setPhone("");
      setEmail("");
      setOpen(false);
      setMessage("Cliente creado y seleccionado.");
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "No pudimos crear el cliente.");
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className={styles.box} aria-label="Crear cliente para la reserva">
      <button
        type="button"
        className={styles.toggle}
        aria-expanded={open}
        onClick={() => {
          setOpen(value => !value);
          setMessage("");
        }}
      >
        {open ? "Ocultar nuevo cliente" : "+ Nuevo cliente"}
      </button>

      {open && (
        <div className={styles.form}>
          <div className={styles.grid}>
            <label>
              <span>Nombre</span>
              <input
                aria-label="Nombre del cliente"
                value={name}
                onChange={event => setName(event.currentTarget.value)}
                maxLength={150}
              />
            </label>
            <label>
              <span>Teléfono</span>
              <input
                aria-label="Teléfono del cliente"
                value={phone}
                onChange={event => setPhone(event.currentTarget.value)}
                maxLength={30}
              />
            </label>
          </div>
          <label>
            <span>Email</span>
            <input
              aria-label="Email del cliente"
              type="email"
              value={email}
              onChange={event => setEmail(event.currentTarget.value)}
              maxLength={180}
            />
          </label>
          {message && <p className={styles.message} role="status">{message}</p>}
          <button
            type="button"
            className={styles.create}
            disabled={saving}
            onClick={submit}
          >
            {saving ? "Creando…" : "Crear cliente"}
          </button>
        </div>
      )}

      {!open && message && <p className={styles.message} role="status">{message}</p>}
    </section>
  );
}
