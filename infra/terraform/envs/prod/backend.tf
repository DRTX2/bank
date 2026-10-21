# Remote state: versioned, encrypted S3 bucket from ../../bootstrap. S3-native locking (no DynamoDB table).
terraform {
  backend "s3" {
    bucket       = "kuntur-terraform-state"
    key          = "prod/platform.tfstate"
    region       = "us-east-1"
    encrypt      = true
    use_lockfile = true
  }
}
