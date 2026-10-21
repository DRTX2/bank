variable "name" {
  type = string
}

variable "create_provider" {
  description = "The OIDC provider is account-wide: create it in one environment only."
  type        = bool
  default     = true
}

variable "github_repository" {
  description = "owner/repo"
  type        = string
}

variable "github_environment" {
  type = string
}

variable "ecr_repository_arns" {
  type = list(string)
}

variable "ecs_service_arns" {
  type = list(string)
}

variable "task_role_arns" {
  type = list(string)
}

variable "site_bucket_arn" {
  type = string
}

variable "distribution_arn" {
  type = string
}
