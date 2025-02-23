// Copyright 2023 The Fury Authors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

use super::types::{FieldType, Language};

#[derive(thiserror::Error, Debug)]
pub enum Error {
    #[error("Field is not Option type, can't be deserialize of None")]
    Null,

    #[error("Fury on Rust not support Ref type")]
    Ref,

    #[error("Fury on Rust not support RefValue type")]
    RefValue,

    #[error("BadRefFlag")]
    BadRefFlag,

    #[error("Bad FieldType; expected: {expected:?}, actual: {actial:?}")]
    FieldType { expected: FieldType, actial: i16 },

    #[error("Bad timestamp; out-of-range number of milliseconds")]
    NaiveDateTime,

    #[error("Bad date; out-of-range")]
    NaiveDate,

    #[error("Schema is not consistent; expected: {expected:?}, actual: {actial:?}")]
    StructHash { expected: u32, actial: u32 },

    #[error("Bad Tag Type: {0}")]
    TagType(u8),

    #[error("Only Xlang supported; receive: {language:?}")]
    UnsupportLanguage { language: Language },

    #[error("Unsupported Language Code; receive: {code:?}")]
    UnsupportLanguageCode { code: u8 },
}
