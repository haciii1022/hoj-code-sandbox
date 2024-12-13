#ifndef EXECUTE_H
#define EXECUTE_H
#include "ExecuteMessage.h"
ExecuteMessage execute_user_code(const char* executable,
                                 const char* input_file_path,
                                 const char* user_output_file_path,
                                 long time_out);

#endif  // EXECUTE_H
