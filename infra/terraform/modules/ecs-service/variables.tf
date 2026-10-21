variable "name" {
  type = string
}

variable "image" {
  description = "Image reference; pin by digest in production (repo@sha256:...)."
  type        = string
}

variable "cluster_arn" {
  type = string
}

variable "service_connect_namespace_arn" {
  type = string
}

variable "cpu" {
  type    = number
  default = 512
}

variable "memory" {
  type    = number
  default = 1024
}

variable "ports" {
  type = list(object({
    name         = string
    port         = number
    app_protocol = string
    discoverable = bool
  }))
}

variable "environment" {
  type    = map(string)
  default = {}
}

variable "secrets" {
  description = "Env var name => Secrets Manager ARN (optionally with :json-key:: suffix)."
  type        = map(string)
  default     = {}
}

variable "subnet_ids" {
  type = list(string)
}

variable "security_group_ids" {
  type = list(string)
}

variable "target_group_arn" {
  type    = string
  default = null
}

variable "task_policy_json" {
  type    = string
  default = null
}

variable "min_count" {
  type    = number
  default = 2
}

variable "max_count" {
  type    = number
  default = 6
}

variable "kms_key_arn" {
  type = string
}

variable "log_retention_days" {
  type    = number
  default = 365
}
