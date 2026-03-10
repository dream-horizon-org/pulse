import { Container, Title, Text, Stack, Card, Group, Button, Badge, List, ThemeIcon, Box } from '@mantine/core';
import { IconCheck, IconMail, IconRocket, IconBuilding } from '@tabler/icons-react';
import { useTenantContext } from '../../contexts';
import classes from './Pricing.module.css';

export function Pricing() {
  const { tier } = useTenantContext();
  const currentPlan = tier || 'free';

  const handleContactUs = () => {
    window.open('mailto:sales@yourcompany.com?subject=Enterprise Plan Inquiry', '_blank');
  };

  return (
    <Box className={classes.container}>
      <Container size="lg" py="xl">
        <Stack gap="xl" align="center">
          {/* Header */}
          <Stack gap="md" align="center" className={classes.header}>
            <Title order={1} className={classes.title}>
              Choose Your Plan
            </Title>
            <Text size="lg" c="dimmed" ta="center" maw={600}>
              Start with our free plan and upgrade as you grow. No credit card required.
            </Text>
          </Stack>

          {/* Pricing Cards */}
          <div className={classes.pricingGrid}>
            {/* Free Plan */}
            <Card
              shadow="md"
              padding="xl"
              radius="lg"
              className={`${classes.pricingCard} ${currentPlan === 'free' ? classes.currentPlan : ''}`}
              withBorder
            >
              <Stack gap="lg">
                {/* Plan Header */}
                <div>
                  <Group justify="space-between" mb="xs">
                    <Group gap="xs">
                      <ThemeIcon size="lg" radius="md" variant="light" color="teal">
                        <IconRocket size={20} />
                      </ThemeIcon>
                      <Title order={2}>Free</Title>
                    </Group>
                    {currentPlan === 'free' && (
                      <Badge color="teal" variant="filled" size="lg">
                        Current Plan
                      </Badge>
                    )}
                  </Group>
                  <Text c="dimmed" size="sm">
                    Perfect for getting started
                  </Text>
                </div>

                {/* Price */}
                <div>
                  <Group align="baseline" gap="xs">
                    <Text size="48px" fw={700} lh={1}>
                      $0
                    </Text>
                    <Text size="lg" c="dimmed">
                      / month
                    </Text>
                  </Group>
                </div>

                {/* Features */}
                <List
                  spacing="sm"
                  size="sm"
                  icon={
                    <ThemeIcon color="teal" size={20} radius="xl">
                      <IconCheck size={12} />
                    </ThemeIcon>
                  }
                >
                  <List.Item>1 Project</List.Item>
                  <List.Item>Up to 5 team members</List.Item>
                  <List.Item>Basic analytics & monitoring</List.Item>
                  <List.Item>7 days data retention</List.Item>
                  <List.Item>Community support</List.Item>
                  <List.Item>SDK for Android, iOS, React Native</List.Item>
                </List>

                {/* CTA Button */}
                <Button
                  fullWidth
                  size="lg"
                  variant={currentPlan === 'free' ? 'filled' : 'outline'}
                  color="teal"
                  disabled={currentPlan === 'free'}
                >
                  {currentPlan === 'free' ? 'Current Plan' : 'Get Started'}
                </Button>
              </Stack>
            </Card>

            {/* Enterprise Plan */}
            <Card
              shadow="xl"
              padding="xl"
              radius="lg"
              className={`${classes.pricingCard} ${classes.enterpriseCard}`}
              withBorder
            >
              <Stack gap="lg">
                {/* Plan Header */}
                <div>
                  <Group justify="space-between" mb="xs">
                    <Group gap="xs">
                      <ThemeIcon size="lg" radius="md" variant="gradient" gradient={{ from: 'teal', to: 'blue', deg: 135 }}>
                        <IconBuilding size={20} />
                      </ThemeIcon>
                      <Title order={2}>Enterprise</Title>
                    </Group>
                    <Badge variant="gradient" gradient={{ from: 'teal', to: 'blue', deg: 135 }} size="lg">
                      Popular
                    </Badge>
                  </Group>
                  <Text c="dimmed" size="sm">
                    For teams that need more
                  </Text>
                </div>

                {/* Price */}
                <div>
                  <Group align="baseline" gap="xs">
                    <Text size="48px" fw={700} lh={1}>
                      Custom
                    </Text>
                  </Group>
                  <Text size="sm" c="dimmed" mt="xs">
                    Tailored to your needs
                  </Text>
                </div>

                {/* Features */}
                <List
                  spacing="sm"
                  size="sm"
                  icon={
                    <ThemeIcon 
                      variant="gradient" 
                      gradient={{ from: 'teal', to: 'blue', deg: 135 }}
                      size={20} 
                      radius="xl"
                    >
                      <IconCheck size={12} />
                    </ThemeIcon>
                  }
                >
                  <List.Item><strong>Unlimited projects</strong></List.Item>
                  <List.Item><strong>Unlimited team members</strong></List.Item>
                  <List.Item>Advanced analytics & monitoring</List.Item>
                  <List.Item>Custom data retention</List.Item>
                  <List.Item>Priority support & SLA</List.Item>
                  <List.Item>Custom integrations</List.Item>
                  <List.Item>On-premise deployment option</List.Item>
                  <List.Item>Dedicated account manager</List.Item>
                </List>

                {/* CTA Button */}
                <Button
                  fullWidth
                  size="lg"
                  variant="gradient"
                  gradient={{ from: 'teal', to: 'blue', deg: 135 }}
                  leftSection={<IconMail size={18} />}
                  onClick={handleContactUs}
                >
                  Contact Sales
                </Button>
              </Stack>
            </Card>
          </div>

          {/* Footer Note */}
          <Text size="sm" c="dimmed" ta="center" mt="xl">
            Need help choosing? <Text component="span" c="teal" fw={600} style={{ cursor: 'pointer' }}>Contact our team</Text> for personalized recommendations.
          </Text>
        </Stack>
      </Container>
    </Box>
  );
}
