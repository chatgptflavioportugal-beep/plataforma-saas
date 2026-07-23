export interface SendMessageResult {
  status: string
}

export interface ApiError {
  error: string
  message?: string
  feature?: string
  requiredPlan?: string
}
