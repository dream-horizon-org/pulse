terraform {
  required_version = ">= 1.3.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = var.aws_region
}

############################################
# ADD: Discover VPC ID from one of the subnets
############################################
data "aws_subnet" "any_clickhouse_subnet" {
  id = values(var.subnets)[0]
}

locals {
  azs = sort(keys(var.subnets))  # stable ordering

  # Create all (shard, replica) pairs
  shard_replica_pairs = setproduct(
    range(var.shard_count),   # 0..shard_count-1
    range(var.replica_count)  # 0..replica_count-1
  )

  # Keyed map for for_each
  clickhouse_instances = {
    for pair in local.shard_replica_pairs :
    "shard-${pair[0] + 1}-replica-${pair[1] + 1}" => {
      shard_index   = pair[0] + 1
      replica_index = pair[1] + 1
      az            = local.azs[pair[1]]                 # replica 1 -> az[0], replica 2 -> az[1], etc.
      subnet_id     = var.subnets[local.azs[pair[1]]]
    }
  }

  # Subnets where ClickHouse nodes exist (unique list)
  clickhouse_subnet_ids = distinct([
    for _, v in local.clickhouse_instances : v.subnet_id
  ])
}

############################################
# ADD: NLB for ClickHouse Native Port 9000
############################################
resource "aws_lb" "clickhouse_nlb" {
  name               = "${var.cluster_name}-nlb"
  internal           = true
  load_balancer_type = "network"

  # Put NLB only in the AZs/subnets where ClickHouse nodes exist
  subnets = local.clickhouse_subnet_ids

  # For your case (clients in 3 AZs, CH in 2 AZs), keep this ON for smoother routing/failover
  enable_cross_zone_load_balancing = true

  enable_deletion_protection = false

  security_groups = var.vpc_security_group_ids

  tags = {
    Name        = "${var.cluster_name}-nlb"
    ClusterName = var.cluster_name
    service     = "pulse"
  }
}

resource "aws_lb_target_group" "clickhouse_tg" {
  name        = "${var.cluster_name}-tg"
  port        = 9000
  protocol    = "TCP"
  vpc_id      = data.aws_subnet.any_clickhouse_subnet.vpc_id
  target_type = "instance"

  health_check {
    protocol            = "TCP"
    port                = "9000"
    interval            = 10
    healthy_threshold   = 3
    unhealthy_threshold = 3
  }

  tags = {
    Name        = "${var.cluster_name}-tg"
    ClusterName = var.cluster_name
    service     = "pulse"
  }
}

resource "aws_lb_listener" "clickhouse_9000" {
  load_balancer_arn = aws_lb.clickhouse_nlb.arn
  port              = 9000
  protocol          = "TCP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.clickhouse_tg.arn
  }
}

############################################
# ADD: Attach all ClickHouse EC2 instances (all shard/replica nodes) to the NLB TG
############################################
resource "aws_lb_target_group_attachment" "clickhouse_attach" {
  for_each = aws_instance.clickhouse

  target_group_arn = aws_lb_target_group.clickhouse_tg.arn
  target_id        = each.value.id
  port             = 9000
}


resource "aws_route53_record" "clickhouse_nlb" {
  zone_id = var.private_zone_id
  name    = "${var.cluster_name}.${var.private_zone_domain}"
  type    = "A"

  alias {
    name                   = aws_lb.clickhouse_nlb.dns_name
    zone_id                = aws_lb.clickhouse_nlb.zone_id
    evaluate_target_health = true
  }
}

# -------------------------------
# EC2 instances for ClickHouse
# -------------------------------
resource "aws_instance" "clickhouse" {
  for_each               = local.clickhouse_instances
  ami                    = var.ami_id
  instance_type          = var.instance_type
  availability_zone      = each.value.az
  subnet_id              = each.value.subnet_id

  vpc_security_group_ids = var.vpc_security_group_ids
  key_name               = var.key_name
  iam_instance_profile   = var.iam_instance_profile
  associate_public_ip_address = false
  depends_on = [aws_route53_record.keeper]

  # Tag each instance with cluster + shard index
  tags = {
    Name             = "${var.cluster_name}-shard-${each.value.shard_index}-replica-${each.value.replica_index}"
    ClusterName      = var.cluster_name
    ShardIndex       = each.value.shard_index
    ReplicaIndex     = each.value.replica_index
    AvailabilityZone = each.value.az
    service          = "pulse"
  }

  # Bootstrap script: mount secondary volume & configure ClickHouse
  user_data = templatefile("${path.module}/user_data.sh.tpl", {
    cluster_name    = var.cluster_name
    shard_index     = each.value.shard_index
    shard_count     = var.shard_count
    replica_index   = each.value.replica_index
    replica_count   = var.replica_count
    data_device     = "/dev/xvdb"             # device name we'll attach below
    data_mount_path = "/var/lib/clickhouse"   # adjust to your AMI's ClickHouse data path
    private_zone    = var.private_zone_domain # used to build hostnames
    keeper_count    = var.keeper_count
    keeper_port     = 9181
    user_name       = var.clickhouse_user
    password        = var.clickhouse_password
  })

  # Root volume (OS)
  root_block_device {
    volume_type           = "gp3"
    volume_size           = 20
    delete_on_termination = true
  }

  # Secondary data volume (will be /dev/xvdb inside the instance)
  ebs_block_device {
    device_name           = "/dev/xvdb"
    volume_type           = var.data_volume_type
    volume_size           = var.data_volume_size
    iops                  = 3000
    throughput            = 125
    encrypted             = false
    delete_on_termination = true
  }

  volume_tags = {
    Name        = "${var.cluster_name}-shard-${each.value.shard_index}-replica-${each.value.replica_index}-volume"
    ClusterName = var.cluster_name
    ShardIndex  = each.value.shard_index
    ReplicaIndex= each.value.replica_index
    Role        = "clickhouse"
    service     = "pulse"
  }

  metadata_options {
    http_tokens = "required"
  }
}

# -------------------------------
# Route53 records for each shard
# These hostnames will be used in cluster config
# -------------------------------
resource "aws_route53_record" "clickhouse_node" {
  for_each = local.clickhouse_instances
  zone_id = var.private_zone_id

  # e.g. shard-1-replica-2.cluster.internal.example.com
  name = "${each.key}.${var.cluster_name}.${var.private_zone_domain}"
  type = "A"
  ttl  = 10

  records = [aws_instance.clickhouse[each.key].private_ip]
}

resource "aws_route53_record" "keeper" {
  count   = var.keeper_count
  zone_id = var.private_zone_id

  # keeper-1.<cluster>.<zone>, keeper-2....
  name = "keeper-${count.index + 1}.${var.cluster_name}.${var.private_zone_domain}"
  type = "A"
  ttl  = 10

  records = [aws_instance.keeper[count.index].private_ip]
}

resource "aws_instance" "keeper" {
  count                  = var.keeper_count
  ami                    = var.keeper_ami_id
  instance_type          = var.keeper_instance_type
  key_name               = var.key_name
  vpc_security_group_ids = var.vpc_security_group_ids
  iam_instance_profile   = var.iam_instance_profile

  availability_zone      = local.azs[count.index]
  subnet_id              = var.subnets[local.azs[count.index]]
  associate_public_ip_address = false
  user_data = templatefile("${path.module}/keeper_user_data.sh.tpl", {
      cluster_name = var.cluster_name
      private_zone = var.private_zone_domain
      keeper_count = var.keeper_count
      keeper_id    = count.index + 1
      raft_port    = 9234
      keeper_port  = 9181
    })

  tags = {
    Name                = "pulse-clickhouse-keeper-${count.index + 1}"
    component_name      = "pulse-clickhouse-keeper"
    service             = "pulse"
  }

  root_block_device {
    volume_size = 20
    volume_type = "gp3"
  }
  volume_tags = {
    Name                = "zookeeper-node-${count.index + 1}-root"
    component_name      = "clickhouse-keeper"
    service             = "pulse"
  }

  metadata_options {
    http_tokens = "required"
  }
}

# -------------------------------
# Outputs
# -------------------------------
output "clickhouse_private_ips" {
  value = { for k, inst in aws_instance.clickhouse : k => inst.private_ip }
}

output "clickhouse_hostnames" {
  value = [for k in keys(local.clickhouse_instances) :
    "${k}.${var.cluster_name}.${var.private_zone_domain}"
  ]
}

output "clickhouse_nlb_dns_name" {
  value = aws_lb.clickhouse_nlb.dns_name
}

output "clickhouse_nlb_endpoint_9000" {
  value = "${aws_lb.clickhouse_nlb.dns_name}:9000"
}

output "clickhouse_private_endpoint" {
  value = "${var.cluster_name}.${var.private_zone_domain}:9000"
}
