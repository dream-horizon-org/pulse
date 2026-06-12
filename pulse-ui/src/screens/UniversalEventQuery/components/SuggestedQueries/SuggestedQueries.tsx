import { useState } from "react";
import { IconQuestionMark } from "@tabler/icons-react";
import { Button } from "@mantine/core";
import { SuggestedQueriesContent } from "./SuggestedQueriesContent";
import { DModal as Modal } from "../../../../components/Modal";

import { UNIVERSAL_QUERY_TEXTS } from "../../UniversalEventQuery.constants";

export const SuggestedQueries = () => {
  const [showModal, setShowModal] = useState<boolean>(false);

  const handleButtonClick = () => {
    setShowModal((prev) => !prev);
  };

  const renderContent = () => {
    return <SuggestedQueriesContent />;
  };

  const QUESTION_ICON = <IconQuestionMark size={20} />;

  return (
    <>
      <Button
        leftSection={QUESTION_ICON}
        variant="outline"
        onClick={handleButtonClick}
      >
        {UNIVERSAL_QUERY_TEXTS.SUGGESTED_QUERIES}
      </Button>
      {showModal && (
        <Modal
          title="Suggested Queries"
          size="60%"
          renderContent={renderContent}
          onClose={handleButtonClick}
          visible={showModal}
        />
      )}
    </>
  );
};
