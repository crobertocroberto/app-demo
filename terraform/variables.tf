variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "us-east-1"
}

variable "project_name" {
  description = "Project name for resource tagging"
  type        = string
  default     = "demo-cicd"
}

variable "instance_type" {
  description = "EC2 instance type"
  type        = string
  default     = "t3.micro"
}

variable "key_name" {
  description = "SSH key pair name"
  type        = string
  default     = "CR-3htp-Col"
}

variable "subnet_id" {
  description = "Subnet ID for the instance"
  type        = string
  default     = "subnet-0a00761dea3d7c33d"
}

variable "security_group_id" {
  description = "Existing security group ID to use"
  type        = string
  default     = "sg-009579191e995a2bd"
}
