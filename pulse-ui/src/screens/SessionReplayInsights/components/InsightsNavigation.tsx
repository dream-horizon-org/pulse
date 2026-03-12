import { Group, Paper, UnstyledButton } from '@mantine/core';
import { useState, useEffect } from 'react';
import classes from './InsightsNavigation.module.css';

interface Section {
  id: string;
  label: string;
}

const SECTIONS: Section[] = [
  { id: 'business-impact', label: 'Business Impact' },
  { id: 'session-health', label: 'Session Health' },
  { id: 'critical-interactions', label: 'Critical Interactions' },
  { id: 'whats-breaking', label: "What's Breaking" },
  { id: 'time-patterns', label: 'Time Patterns' },
];

export function InsightsNavigation() {
  const [activeSection, setActiveSection] = useState('business-impact');

  useEffect(() => {
    const handleScroll = () => {
      const sections = SECTIONS.map(s => document.getElementById(s.id)).filter(Boolean);
      const scrollPosition = window.scrollY + 150;
      
      // Check if we're near the bottom of the page
      const windowHeight = window.innerHeight;
      const documentHeight = document.documentElement.scrollHeight;
      const isNearBottom = windowHeight + window.scrollY >= documentHeight - 100;
      
      // If near bottom, highlight the last section
      if (isNearBottom) {
        setActiveSection(SECTIONS[SECTIONS.length - 1].id);
        return;
      }

      // Otherwise, find the current section
      for (let i = sections.length - 1; i >= 0; i--) {
        const section = sections[i];
        if (section && section.offsetTop <= scrollPosition) {
          setActiveSection(SECTIONS[i].id);
          break;
        }
      }
    };

    window.addEventListener('scroll', handleScroll);
    handleScroll(); // Run on mount
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  const scrollToSection = (sectionId: string) => {
    const element = document.getElementById(sectionId);
    if (element) {
      const offset = 120; // Account for sticky header
      const elementPosition = element.getBoundingClientRect().top + window.pageYOffset;
      window.scrollTo({
        top: elementPosition - offset,
        behavior: 'smooth'
      });
    }
  };

  return (
    <Paper className={classes.stickyNav}>
      <Group gap="md" wrap="nowrap" className={classes.navGroup}>
        {SECTIONS.map((section) => (
          <UnstyledButton
            key={section.id}
            onClick={() => scrollToSection(section.id)}
            className={`${classes.navButton} ${activeSection === section.id ? classes.active : ''}`}
          >
            {section.label}
          </UnstyledButton>
        ))}
      </Group>
    </Paper>
  );
}
