export function getToken() {
    return localStorage.getItem("token");
}

export function saveToken(token: string) {
    localStorage.setItem("token", token);
}

export function removeToken() {
    localStorage.removeItem("token");
}

export function isAuthenticated() {
    return !!getToken();
}

export async function adminRequest<T>(
    endpoint: string,
    options: RequestInit = {}
): Promise<T> {
    const token = getToken();

    const response = await fetch(`http://localhost:8081/api${endpoint}`, {
        ...options,
        headers: {
            "Content-Type": "application/json",
            ...(token ? { Authorization: `Bearer ${token}` } : {}),
            ...options.headers,
        },
    });

    if (!response.ok) {
        const text = await response.text();
        throw new Error(text || `Erro na requisição: ${response.status}`);
    }

    if (response.status === 204) {
        return null as T;
    }

    return response.json();
}