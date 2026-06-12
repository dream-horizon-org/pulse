import { FC, useRef, useState, useEffect } from "react";
import { Tooltip } from "@mantine/core";
import "./Ellipsis.css";

interface EllipsisProps {
  text: string;
  className?: string;
  maxWidth?: number;
}

const Ellipsis: FC<EllipsisProps> = ({ text, className, maxWidth = 200 }) => {
  const [isOverflowed, setIsOverflowed] = useState(false);
  const textRef = useRef<HTMLSpanElement>(null);

  useEffect(() => {
    if (textRef.current) {
      setIsOverflowed(
        textRef.current.scrollWidth > textRef.current.clientWidth,
      );
    }
  }, [text]);

  return (
    <Tooltip label={text} disabled={!isOverflowed} withArrow position="top">
      <div
        className={`ellipsis-container ${className || ""}`}
        style={{ maxWidth }}
      >
        <span ref={textRef} className="ellipsis-text">
          {text}
        </span>
      </div>
    </Tooltip>
  );
};

export { Ellipsis };
