variable "name" {
  type = string
}

variable "account_id" {
  type = string
}

variable "domain_name" {
  type = string
}

variable "certificate_arn_us_east_1" {
  description = "CloudFront certificates must live in us-east-1."
  type        = string
}

variable "api_origin_domain" {
  description = "DNS name that resolves to the ALB and matches its certificate."
  type        = string
}

variable "origin_verify_secret" {
  type      = string
  sensitive = true
}

variable "cloudfront_web_acl_arn" {
  description = "Optional global (us-east-1) WAF for the edge; the API already has a regional WAF on the ALB."
  type        = string
  default     = null
}

variable "logs_bucket" {
  type = string
}

variable "kms_key_arn" {
  description = "CMK for the site bucket; its key policy must allow cloudfront.amazonaws.com to decrypt."
  type        = string
}
