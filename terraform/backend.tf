terraform {
  backend "local" {
    path = "/opt/terraform-state/demo-cicd/terraform.tfstate"
  }
}
