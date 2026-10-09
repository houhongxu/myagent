export class ApiError extends Error {
  constructor(message: string, public status = 0) { super(message) }
}

export async function checked(response: Response) {
  if (!response.ok) {
    const data = await response.json().catch(() => null) as { message?: string } | null
    throw new ApiError(data?.message || '请求失败，请检查后端是否启动', response.status)
  }
  return response
}

export async function json<T>(url: string, options?: RequestInit): Promise<T> {
  const response = await checked(await fetch(`/api${url}`, {
    ...options,
    signal: options?.signal ?? AbortSignal.timeout(15000),
    headers: { 'Content-Type': 'application/json', ...options?.headers },
  }))
  return response.json() as Promise<T>
}

