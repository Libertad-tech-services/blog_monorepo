import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { saveToken } from "../../api/adminApi";
// @ts-ignore
import "./adminCss.css";

const API_URL = "http://localhost:8081";

interface LoginResponse {
  token: string | null;
  type: string;
  requiresTwoFactor: boolean;
  temporaryToken: string | null;
}

export default function LoginPage() {
  const navigate = useNavigate();

  const [email, setEmail] = useState("admin@empresa.com");
  const [password, setPassword] = useState("admin123");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [requiresTwoFactor, setRequiresTwoFactor] = useState(false);
  const [temporaryToken, setTemporaryToken] = useState("");
  const [twoFactorCode, setTwoFactorCode] = useState("");

  async function handleLogin(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();

    try {
      setLoading(true);
      setError("");

      const response = await fetch(`${API_URL}/api/auth/login`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ email, password }),
      });

      if (!response.ok) {
        throw new Error("Email ou senha inválidos.");
      }

      const data: LoginResponse = await response.json();

      if (data.requiresTwoFactor && data.temporaryToken) {
        setRequiresTwoFactor(true);
        setTemporaryToken(data.temporaryToken);
        return;
      }

      if (data.token) {
        saveToken(data.token);
        navigate("/painel-secreto");
        return;
      }

      throw new Error("Resposta de login inválida.");
    } catch (error) {
      console.error(error);
      setError("Não foi possível fazer login. Verifique email e senha.");
    } finally {
      setLoading(false);
    }
  }

  async function handleTwoFactorSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();

    try {
      setLoading(true);
      setError("");

      const response = await fetch(`${API_URL}/api/auth/login/2fa`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          temporaryToken,
          code: twoFactorCode,
        }),
      });

      if (!response.ok) {
        throw new Error("Código inválido.");
      }

      const data: LoginResponse = await response.json();

      if (data.token) {
        saveToken(data.token);
        navigate("/painel-secreto");
        return;
      }

      throw new Error("Token não retornado após validação 2FA.");
    } catch (error) {
      console.error(error);
      setError("Código 2FA inválido ou expirado.");
    } finally {
      setLoading(false);
    }
  }

  if (requiresTwoFactor) {
    return (
      <main className="login-page">
        <section className="login-card">
          <h1>Verificação em duas etapas</h1>
          <p>Digite o código de 6 dígitos do seu app authenticator.</p>

          {error && <div className="admin-error">{error}</div>}

          <form onSubmit={handleTwoFactorSubmit}>
            <label>Código</label>
            <input
              value={twoFactorCode}
              onChange={(event) => setTwoFactorCode(event.target.value)}
              maxLength={6}
              inputMode="numeric"
              placeholder="000000"
            />

            <button type="submit" disabled={loading}>
              {loading ? "Validando..." : "Validar código"}
            </button>
          </form>
        </section>
      </main>
    );
  }

  return (
    <main className="login-page">
      <section className="login-card">
        <div className="login-brand">
          <div>
            <img src="/logo-header.png" alt="logo" width="150" />
          </div>
        </div>

        <h1>Acesso interno</h1>
        <p>Entre para gerenciar posts, categorias e comentários.</p>

        {error && <div className="admin-error">{error}</div>}

        <form onSubmit={handleLogin}>
          <label>Email</label>
          <input
            type="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
          />

          <label>Senha</label>
          <input
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
          />

          <button type="submit" disabled={loading}>
            {loading ? "Entrando..." : "Entrar no painel"}
          </button>
        </form>
      </section>
    </main>
  );
}