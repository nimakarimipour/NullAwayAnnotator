package edu.ucr.cs.riple.autofixer.explorers;

import edu.ucr.cs.riple.autofixer.AutoFixer;
import edu.ucr.cs.riple.autofixer.metadata.index.Bank;
import edu.ucr.cs.riple.autofixer.metadata.index.Error;
import edu.ucr.cs.riple.autofixer.metadata.index.FixEntity;
import edu.ucr.cs.riple.injector.Fix;

public class BasicExplorer extends Explorer {

  public BasicExplorer(AutoFixer autoFixer, Bank<Error> errorBank, Bank<FixEntity> fixBank) {
    super(autoFixer, errorBank, fixBank);
  }

  @Override
  public boolean isApplicable(Fix fix) {
    return true;
  }

  @Override
  public boolean requiresInjection(Fix fix) {
    return true;
  }
}
