output "web_url" {
  value = "https://${var.domain_name}"
}

output "cloudfront_domain" {
  description = "Point the domain_name CNAME/ALIAS here."
  value       = module.web.distribution_domain_name
}

output "alb_dns_name" {
  description = "Point api_origin_domain CNAME here."
  value       = module.edge.alb_dns_name
}

output "ecr_repositories" {
  value = { for k, r in aws_ecr_repository.this : k => r.repository_url }
}

output "github_deploy_role_arn" {
  value = module.github.deploy_role_arn
}

output "web_bucket" {
  value = module.web.bucket_name
}

output "cloudfront_distribution_id" {
  value = module.web.distribution_id
}

output "db_master_secret_arn" {
  value = module.data.db_master_secret_arn
}
