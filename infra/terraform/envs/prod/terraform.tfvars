# Production: 3 AZs, NAT per AZ, Multi-AZ database, deletion protection everywhere.
region                      = "us-east-1"
environment                 = "prod"
vpc_cidr                    = "10.50.0.0/16"
az_count                    = 3
single_nat_gateway          = false
deletion_protection         = true
db_instance_class           = "db.r7g.large"
db_multi_az                 = true
min_tasks                   = 3
max_tasks                   = 12
domain_name                 = "app.kuntur.example"
api_origin_domain           = "origin.kuntur.example"
alb_certificate_arn         = "arn:aws:acm:us-east-1:111111111111:certificate/replace-me"
cloudfront_certificate_arn  = "arn:aws:acm:us-east-1:111111111111:certificate/replace-me"
github_repository           = "DRTX2/bank"
create_github_oidc_provider = false
alarm_email                 = "oncall@kuntur.example"
