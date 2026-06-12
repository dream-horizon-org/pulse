import React, { useState } from "react";
import { Button } from "@mantine/core";
import { IconCopy, IconCheck } from "@tabler/icons-react";
import classes from "./QueryListItem.module.css";

export interface QueryListItemProps {
  header?: string;
  subHeader?: string;
  query: string;
}

const customSQLFormatter = (query: string): string[] => {
  return query
    .replace(/\s+/g, " ")
    .replace(
      /\b(SELECT|FROM|WHERE|AND|OR|JOIN|ON|ORDER BY|GROUP BY|LIMIT|INSERT INTO|VALUES|UPDATE|SET|DELETE FROM|AS)\b/gi,
      "\n$1",
    )
    .split("\n")
    .map((line) => line.trim())
    .filter((line) => line.length > 0);
};

export const QueryListItem: React.FC<QueryListItemProps> = ({
  header,
  subHeader,
  query,
}) => {
  const [copied, setCopied] = useState(false);

  const handleCopy = () => {
    navigator.clipboard.writeText(query).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    }).catch(() => {
      // Clipboard permission denied — fall back to execCommand
      try {
        const el = document.createElement("textarea");
        el.value = query;
        el.style.position = "fixed";
        el.style.opacity = "0";
        document.body.appendChild(el);
        el.select();
        document.execCommand("copy");
        document.body.removeChild(el);
        setCopied(true);
        setTimeout(() => setCopied(false), 1500);
      } catch {
        // Silent fail — copy unavailable
      }
    });
  };

  const formattedQuery = customSQLFormatter(query);

  return (
    <>
      <div className={classes.headerContainer}>
        {header ? <span className={classes.header}>{header}</span> : null}
        {subHeader ? (
          <span className={classes.subHeader}>{subHeader}</span>
        ) : null}
      </div>
      <div className={classes.listItemContainer}>
        <div className={classes.queryContent}>
          {formattedQuery.map((line, index) => (
            <div key={index}>{line}</div>
          ))}
        </div>

        <Button
          onClick={handleCopy}
          variant="subtle"
          className={classes.copyButton}
        >
          {copied ? <IconCheck size={16} /> : <IconCopy size={16} />}
        </Button>
      </div>
    </>
  );
};
