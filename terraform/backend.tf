terraform {
  backend "s3" {
    bucket = "3htp-vault-demos"
    key    = "app-demo/terraform.tfstate"
    region = "us-east-1"
  }
}
